package dev.dskripchenko.laravelapi.markup

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * Paints the markup inside the docblock.
 *
 * Colouring only. Every complaint this class used to make now lives in an
 * inspection, where the severity can be lowered, the finding suppressed and the
 * rule switched off — none of which an annotator can offer.
 *
 * PhpStorm treats an unknown tag as one grey blob of text, so `@input` reads
 * like prose — which is how a missing `$` or a stray word survives review. The
 * colours are not decoration: the moment the type stops being coloured, the
 * generator has stopped understanding the line.
 */
class TagAnnotator : Annotator {

    companion object {
        val TYPE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "LARAVEL_API_DOC_TYPE", DefaultLanguageHighlighterColors.KEYWORD
        )
        val FORMAT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "LARAVEL_API_DOC_FORMAT", DefaultLanguageHighlighterColors.NUMBER
        )
        val VARIABLE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "LARAVEL_API_DOC_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD
        )
        val REFERENCE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "LARAVEL_API_DOC_REFERENCE", DefaultLanguageHighlighterColors.CLASS_REFERENCE
        )
        val CODE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "LARAVEL_API_DOC_CODE", DefaultLanguageHighlighterColors.NUMBER
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PhpDocTag) return

        val tagName = element.name.removePrefix("@")
        if (tagName !in DocTagGrammar.TAGS) return
        if (!LaravelApiProject.isEnabled(element.project)) return

        val body = element.tagValue
        if (body.isEmpty()) return

        // Where the body starts inside the tag element, so the grammar's
        // offsets can be turned into document ranges.
        val bodyStart = element.textRange.startOffset + element.text.length - body.length

        when (val parsed = DocTagGrammar.parse(tagName, body)) {
            is Parsed.Parameter -> {
                paint(holder, bodyStart, parsed.typeRange, TYPE)
                parsed.formatRange?.let { paint(holder, bodyStart, it, FORMAT) }
                paint(holder, bodyStart, parsed.variableRange, VARIABLE)
            }

            is Parsed.ModelRef -> {
                paint(holder, bodyStart, parsed.modelRange, REFERENCE)
            }

            is Parsed.TemplateRef -> {
                paint(holder, bodyStart, parsed.templateRange, REFERENCE)
            }

            is Parsed.Callable -> paint(holder, bodyStart, parsed.methodRange, REFERENCE)

            is Parsed.Response -> {
                paint(holder, bodyStart, parsed.codeRange, CODE)
                parsed.templateRange?.let {
                    paint(holder, bodyStart, it, REFERENCE)
                }
            }

            is Parsed.Security -> {
                paint(holder, bodyStart, parsed.schemeRange, REFERENCE)
            }

            is Parsed.DefaultOrExample -> paint(holder, bodyStart, parsed.variableRange, VARIABLE)

            is Parsed.Malformed -> Unit

            Parsed.Empty -> Unit
        }
    }



    private fun paint(holder: AnnotationHolder, base: Int, at: IntRange, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range(base, at))
            .textAttributes(key)
            .create()
    }

    private fun range(base: Int, at: IntRange) = TextRange(base + at.first, base + at.last + 1)
}
