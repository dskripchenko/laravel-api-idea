package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.markup.CreateTemplateFix
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed
import dev.dskripchenko.laravelapi.navigation.TemplateLookup

/**
 * A response template nobody declared.
 *
 * The generator writes the name into the spec regardless, as a `$ref` at
 * `#/components/schemas/…` that resolves to nothing. The document still
 * validates, so nothing local complains; it breaks for whoever generates a
 * client from it. One real installation carried 323 of these.
 */
class UnknownTemplateInspection : DocTagInspection() {

    override fun inspect(
        tag: PhpDocTag,
        tagName: String,
        parsed: Parsed,
        holder: ProblemsHolder,
        range: (IntRange) -> TextRange,
    ) {
        val (name, at) = when (parsed) {
            is Parsed.TemplateRef -> parsed.template to parsed.templateRange
            is Parsed.ModelRef -> parsed.model to parsed.modelRange
            is Parsed.Response -> (parsed.template ?: return) to (parsed.templateRange ?: return)
            else -> return
        }

        if (name.isEmpty()) return
        if (name in TemplateLookup.allNames(tag.project)) return

        holder.registerProblem(
            tag,
            "Template '$name' is not declared in getOpenApiTemplates() — " +
                "the generated spec will carry a \$ref pointing at nothing",
            ProblemHighlightType.GENERIC_ERROR,
            range(at),
            CreateTemplateFix(name),
        )
    }
}
