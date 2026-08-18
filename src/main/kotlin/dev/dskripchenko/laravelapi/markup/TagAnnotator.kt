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
import dev.dskripchenko.laravelapi.navigation.SecurityLookup
import dev.dskripchenko.laravelapi.navigation.TemplateLookup

/**
 * Paints the markup inside the docblock and says so when it does not parse.
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

                if (parsed.type.isNotEmpty() && !DocTagGrammar.isKnownType(parsed.type)) {
                    // Not an error: the generator carries on. It carries on by
                    // calling the field a string, which is the part worth
                    // knowing.
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        "Unknown type '${parsed.type}' — the generator will call it 'string'. " +
                            "Known types: ${DocTagGrammar.TYPES.joinToString(", ")}."
                    ).range(range(bodyStart, parsed.typeRange)).create()
                }
            }

            is Parsed.ModelRef -> {
                paint(holder, bodyStart, parsed.modelRange, REFERENCE)
                reportUnknownTemplate(holder, element, bodyStart, parsed.model, parsed.modelRange)
            }

            is Parsed.TemplateRef -> {
                paint(holder, bodyStart, parsed.templateRange, REFERENCE)
                reportUnknownTemplate(holder, element, bodyStart, parsed.template, parsed.templateRange)
            }

            is Parsed.Callable -> paint(holder, bodyStart, parsed.methodRange, REFERENCE)

            is Parsed.Response -> {
                paint(holder, bodyStart, parsed.codeRange, CODE)
                parsed.templateRange?.let {
                    paint(holder, bodyStart, it, REFERENCE)
                    reportUnknownTemplate(holder, element, bodyStart, parsed.template!!, it)
                }

                if (parsed.code < 100 || parsed.code > 599) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "${parsed.code} is not an HTTP status code.")
                        .range(range(bodyStart, parsed.codeRange))
                        .create()
                }
            }

            is Parsed.Security -> {
                paint(holder, bodyStart, parsed.schemeRange, REFERENCE)
                reportUnknownScheme(holder, element, bodyStart, parsed.scheme, parsed.schemeRange)
            }

            is Parsed.DefaultOrExample -> paint(holder, bodyStart, parsed.variableRange, VARIABLE)

            is Parsed.Malformed -> {
                // The generator drops such a line without a word, so the spec
                // simply lacks the field. Saying it here is the whole point.
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "@$tagName does not parse and will be ignored — ${parsed.reason}."
                ).range(TextRange(bodyStart, bodyStart + body.length)).create()
            }

            Parsed.Empty -> Unit
        }
    }

    /**
     * A name nothing declares.
     *
     * The generator does not complain about it — it writes a `${'$'}ref` at
     * `#/components/schemas/Whatever` into a spec that still passes validation,
     * and the break surfaces in whoever generates a client from it.
     * Underlining it here is the earliest anyone can be told.
     */
    private fun reportUnknownTemplate(
        holder: AnnotationHolder,
        element: PsiElement,
        base: Int,
        name: String,
        at: IntRange,
    ) {
        if (name.isEmpty()) return
        if (name in TemplateLookup.allNames(element.project)) return

        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "Template '$name' is not declared in getOpenApiTemplates() — " +
                "the generated spec will carry a \$ref pointing at nothing."
        )
            .range(range(base, at))
            // The name is already written here; retyping it in another file is
            // where the second, slightly different spelling comes from.
            .withFix(CreateTemplateFix(name))
            .create()
    }

    /**
     * A scheme nothing declares.
     *
     * Silent while the project declares no schemes at all — see
     * [SecurityLookup.isInUse]. Once one exists, an unrecognised name means the
     * spec will ask for authentication that `components.securitySchemes` never
     * describes.
     */
    private fun reportUnknownScheme(
        holder: AnnotationHolder,
        element: PsiElement,
        base: Int,
        name: String,
        at: IntRange,
    ) {
        if (name.isEmpty()) return
        if (!SecurityLookup.isInUse(element.project)) return
        if (name in SecurityLookup.allNames(element.project)) return

        holder.newAnnotation(
            HighlightSeverity.ERROR,
            "Security scheme '$name' is not declared in getOpenApiSecurityDefinitions() — " +
                "the spec will reference a scheme it never defines."
        ).range(range(base, at)).create()
    }

    private fun paint(holder: AnnotationHolder, base: Int, at: IntRange, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(range(base, at))
            .textAttributes(key)
            .create()
    }

    private fun range(base: Int, at: IntRange) = TextRange(base + at.first, base + at.last + 1)
}
