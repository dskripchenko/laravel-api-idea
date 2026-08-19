package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.psi.elements.Method
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed
import dev.dskripchenko.laravelapi.validation.MarkupFromRules
import dev.dskripchenko.laravelapi.validation.ValidationRules

/**
 * A field the endpoint validates and the markup does not mention.
 *
 * A different failure from a dangling reference: here both sides are
 * impeccable on their own. The rules are correct, the docblock parses, the
 * generated specification validates — and it describes a different set of
 * fields from the one the endpoint accepts. Nothing but a comparison can see
 * it.
 *
 * Measured on two real applications before this was written: of 52 endpoints
 * with both readable rules and a docblock, nine disagreed. Among them a public
 * integration API whose email delivery could not be called from its own
 * documentation, and a bulk endpoint documenting `items` while requiring `ids`.
 */
class UndocumentedFieldInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is Method) return
                if (!LaravelApiProject.isEnabled(element.project)) return

                val docblock = element.docComment ?: return

                // A method without a single @input is not half-documented — it
                // is a method nobody has documented yet, and saying so field by
                // field would be noise rather than a finding.
                val documented = documentedVariables(docblock)
                if (documented.isEmpty()) return

                for (rule in ValidationRules.of(element)) {
                    val name = MarkupFromRules.fieldName(rule.path) ?: continue
                    if (name in documented) continue

                    holder.registerProblem(
                        rule.anchor,
                        "`${rule.path}` is validated and not documented — the spec will describe " +
                            "a different set of fields from the one this endpoint accepts",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        DocumentFieldFix(rule.path, rule.rules),
                    )
                }
            }
        }

    private fun documentedVariables(docblock: com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment): Set<String> =
        docblock.getTagElementsByName("@input")
            .mapNotNull { (DocTagGrammar.parse("input", it.tagValue) as? Parsed.Parameter)?.variable }
            .toSet()
}
