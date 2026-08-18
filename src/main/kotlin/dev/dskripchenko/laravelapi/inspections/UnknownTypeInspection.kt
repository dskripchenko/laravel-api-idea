package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * A type outside the seven the generator knows.
 *
 * Not an error, because nothing breaks: the generator carries on and calls the
 * field a `string`. That is the part worth knowing — `object|null`, `any` and
 * `datetime` all end up documented as text, and the spec looks deliberate.
 */
class UnknownTypeInspection : DocTagInspection() {

    override fun inspect(
        tag: PhpDocTag,
        tagName: String,
        parsed: Parsed,
        holder: ProblemsHolder,
        range: (IntRange) -> TextRange,
    ) {
        if (parsed !is Parsed.Parameter) return
        if (parsed.type.isEmpty() || DocTagGrammar.isKnownType(parsed.type)) return

        holder.registerProblem(
            tag,
            "Unknown type '${parsed.type}' — the generator will call it 'string'. " +
                "Known types: ${DocTagGrammar.TYPES.joinToString(", ")}",
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            range(parsed.typeRange),
        )
    }
}
