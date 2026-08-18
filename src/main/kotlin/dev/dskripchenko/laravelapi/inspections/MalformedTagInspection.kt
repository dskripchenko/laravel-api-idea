package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * A tag the generator cannot read.
 *
 * It does not complain: the line is dropped and the field never reaches the
 * spec. A missing `$` is enough — `@input string name Description` looks
 * perfectly reasonable and documents nothing.
 */
class MalformedTagInspection : DocTagInspection() {

    override fun inspect(
        tag: PhpDocTag,
        tagName: String,
        parsed: Parsed,
        holder: ProblemsHolder,
        range: (IntRange) -> TextRange,
    ) {
        when (parsed) {
            is Parsed.Malformed -> holder.registerProblem(
                tag,
                "@$tagName does not parse and will be ignored — ${parsed.reason}",
                ProblemHighlightType.GENERIC_ERROR,
            )

            is Parsed.Response -> if (parsed.code < 100 || parsed.code > 599) {
                holder.registerProblem(
                    tag,
                    "${parsed.code} is not an HTTP status code",
                    ProblemHighlightType.GENERIC_ERROR,
                    range(parsed.codeRange),
                )
            }

            else -> Unit
        }
    }
}
