package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed
import dev.dskripchenko.laravelapi.navigation.SecurityLookup

/**
 * A `@security` naming a scheme `getOpenApiSecurityDefinitions()` never
 * declared.
 *
 * The generator writes the name under `security:` regardless, referring to a
 * scheme `components.securitySchemes` does not define. The spec validates and
 * cannot be used to authenticate anything. One installation carried 848 of
 * these, all naming four schemes nobody had declared.
 */
class UnknownSecuritySchemeInspection : DocTagInspection() {

    /**
     * Whether to stay quiet in a project that declares no schemes at all.
     *
     * On by default: such an application has evidently not taken the feature
     * up, and painting every tag red teaches people to look past the plugin
     * rather than at it. Until this was an inspection the choice was made in
     * the code, for everyone.
     */
    @JvmField
    var onlyWhenSchemesExist: Boolean = true

    override fun getOptionsPane(): OptPane = OptPane.pane(
        OptPane.checkbox(
            "onlyWhenSchemesExist",
            "Only when the project declares at least one scheme",
        )
    )

    override fun inspect(
        tag: PhpDocTag,
        tagName: String,
        parsed: Parsed,
        holder: ProblemsHolder,
        range: (IntRange) -> TextRange,
    ) {
        if (parsed !is Parsed.Security || parsed.scheme.isEmpty()) return

        val project = tag.project
        if (onlyWhenSchemesExist && !SecurityLookup.isInUse(project)) return
        if (parsed.scheme in SecurityLookup.allNames(project)) return

        holder.registerProblem(
            tag,
            "Security scheme '${parsed.scheme}' is not declared in getOpenApiSecurityDefinitions() — " +
                "the spec will reference a scheme it never defines",
            ProblemHighlightType.GENERIC_ERROR,
            range(parsed.schemeRange),
        )
    }
}
