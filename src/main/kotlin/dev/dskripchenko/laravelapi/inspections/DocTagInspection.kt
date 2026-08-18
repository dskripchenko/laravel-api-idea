package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * The shared part of every markup inspection: find our tags, parse them once,
 * hand the result over.
 *
 * Inspections rather than annotators, and not for tidiness. An annotator hides
 * its severity in the code, cannot be suppressed, cannot be switched off, and
 * takes no part in "Analyze → Inspect Code". The platform gives all four away
 * with `LocalInspectionTool` — which is also why this plugin will never grow a
 * settings screen of its own.
 */
abstract class DocTagInspection : LocalInspectionTool() {

    /**
     * Called for a tag the plugin recognises, in a project that uses the
     * package. [range] converts an offset inside the tag's body into a range
     * relative to the tag element — which is what ProblemsHolder wants.
     */
    protected abstract fun inspect(
        tag: PhpDocTag,
        tagName: String,
        parsed: Parsed,
        holder: ProblemsHolder,
        range: (IntRange) -> TextRange,
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is PhpDocTag) return

                val tagName = element.name.removePrefix("@")
                if (tagName !in DocTagGrammar.TAGS) return
                if (!LaravelApiProject.isEnabled(element.project)) return

                val body = element.tagValue
                if (body.isEmpty()) return

                // Where the body starts inside the tag, so the grammar's
                // offsets can be turned into ranges the platform understands.
                val bodyStart = element.text.length - body.length

                inspect(element, tagName, DocTagGrammar.parse(tagName, body), holder) { at ->
                    TextRange(bodyStart + at.first, bodyStart + at.last + 1)
                }
            }
        }
}
