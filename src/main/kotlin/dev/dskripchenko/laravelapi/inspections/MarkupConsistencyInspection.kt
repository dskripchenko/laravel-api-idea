package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * The mistakes that only show up when the docblock is read as a whole.
 *
 * A single tag can be perfectly well formed and still wrong in company: a field
 * declared twice, a nested field whose parent nobody declared, two answers for
 * one status code. In each case the generator keeps the last one it saw and
 * says nothing, so the spec quietly describes fewer fields than the docblock
 * does.
 *
 * Deliberately absent: `@default` for a variable with no `@input`. Middleware
 * contributes inputs of its own, and telling a stray default from a legitimate
 * one means resolving the route map and the middleware chain — which `api:lint`
 * does with the application booted, and an inspection would only guess at.
 */
class MarkupConsistencyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is PhpDocComment) return
                if (!LaravelApiProject.isEnabled(element.project)) return

                for (group in listOf("input", "output", "header")) {
                    checkParameters(element.getTagElementsByName("@$group").toList(), group, holder)
                }

                checkResponses(element.getTagElementsByName("@response").toList(), holder)
            }
        }

    private fun checkParameters(tags: List<PhpDocTag>, group: String, holder: ProblemsHolder) {
        val declared = mutableMapOf<String, String>()
        val seen = mutableSetOf<String>()

        for (tag in tags) {
            val parsed = DocTagGrammar.parse(group, tag.tagValue)
            if (parsed !is Parsed.Parameter) continue

            if (!seen.add(parsed.variable)) {
                holder.registerProblem(
                    tag,
                    "@$group \$${parsed.variable} is declared twice; the last one wins",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }

            declared[parsed.variable] = parsed.type
        }

        // The parents are checked afterwards: a nested field may be written
        // before the object that holds it, and order is not the mistake.
        for (tag in tags) {
            val parsed = DocTagGrammar.parse(group, tag.tagValue)
            if (parsed !is Parsed.Parameter) continue

            val parent = DocTagGrammar.parentOf(parsed.variable) ?: continue
            val expected = if (DocTagGrammar.parentIsArray(parsed.variable)) "array" else "object"
            val parentType = declared[parent]

            if (parentType == null) {
                holder.registerProblem(
                    tag,
                    "@$group \$${parsed.variable} is nested under \$$parent, which is never declared",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )

                continue
            }

            if (parentType.isNotEmpty() && parentType != expected) {
                holder.registerProblem(
                    tag,
                    "@$group \$${parsed.variable} needs \$$parent to be `$expected`, and it is declared `$parentType`",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }

    private fun checkResponses(tags: List<PhpDocTag>, holder: ProblemsHolder) {
        val seen = mutableSetOf<Int>()

        for (tag in tags) {
            val parsed = DocTagGrammar.parse("response", tag.tagValue)
            if (parsed !is Parsed.Response) continue

            if (!seen.add(parsed.code)) {
                holder.registerProblem(
                    tag,
                    "@response ${parsed.code} is declared twice; the last one wins and the other body is lost from the spec",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
        }
    }
}
