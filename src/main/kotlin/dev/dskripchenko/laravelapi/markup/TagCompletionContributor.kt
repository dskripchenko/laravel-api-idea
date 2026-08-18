package dev.dskripchenko.laravelapi.markup

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.navigation.SecurityLookup
import dev.dskripchenko.laravelapi.navigation.TemplateLookup

/**
 * Completion inside the markup.
 *
 * The names it offers are the ones nobody can hold in their head: seven types
 * that look like PHP's but are not, the formats OpenAPI accepts, and — above
 * all — template and scheme names, which live in another file and are spelled
 * exactly or not at all.
 *
 * The work is done by hand rather than through references because the tag never
 * hands its references out (see the goto handler for why); the position inside
 * the body is worked out from the caret offset instead.
 */
class TagCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PhpDocTag::class.java),
            TagCompletionProvider()
        )
    }
}

private class TagCompletionProvider : CompletionProvider<CompletionParameters>() {

    private companion object {
        /**
         * The formats worth suggesting. Not a closed set in OpenAPI — anything
         * is allowed — so these are the ones in daily use rather than a law.
         */
        val FORMATS = listOf(
            "date-time", "date", "time", "email", "uuid", "uri", "hostname",
            "ipv4", "ipv6", "password", "byte", "binary", "int32", "int64", "float", "double",
        )

        /** The codes an API actually answers with, in the order it answers them. */
        val CODES = listOf(
            "200" to "OK",
            "201" to "Created",
            "204" to "No Content",
            "400" to "Bad Request",
            "401" to "Unauthorized",
            "403" to "Forbidden",
            "404" to "Not Found",
            "409" to "Conflict",
            "422" to "Unprocessable Entity",
            "429" to "Too Many Requests",
            "500" to "Internal Server Error",
        )
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val position = parameters.position
        if (!LaravelApiProject.isEnabled(position.project)) return

        val tag = PsiTreeUtil.getParentOfType(position, PhpDocTag::class.java, false) ?: return
        val tagName = tag.name.removePrefix("@")
        if (tagName !in DocTagGrammar.TAGS) return

        // The text of the tag up to the caret is all the context needed: the
        // markup is one line, and what may come next follows from what is
        // already written.
        val caretInTag = parameters.offset - tag.textRange.startOffset
        if (caretInTag <= 0 || caretInTag > tag.text.length) return
        val before = tag.text.substring(0, caretInTag).removePrefix("@$tagName")

        when (tagName) {
            "input", "output", "header" -> completeParameter(before, result, position.project)
            "response" -> completeResponse(before, result, position.project)
            "security" -> completeSecurity(before, result, position.project)
        }
    }

    private fun completeParameter(before: String, result: CompletionResultSet, project: com.intellij.openapi.project.Project) {
        val written = before.trimStart()

        // `@input @Mod` — a model reference: the names of declared templates.
        if (written.startsWith("@")) {
            offerTemplates(result.withPrefixMatcher(written.removePrefix("@")), project)

            return
        }

        // `@output {Tem` — a template as the whole response body.
        if (written.startsWith("{")) {
            offerTemplates(result.withPrefixMatcher(written.removePrefix("{")), project)

            return
        }

        // `string(dat` — inside the parentheses, so a format is expected.
        val openParen = written.lastIndexOf('(')
        if (openParen >= 0 && !written.substring(openParen).contains(')')) {
            val prefix = written.substring(openParen + 1)
            FORMATS.forEach {
                result.withPrefixMatcher(prefix).addElement(LookupElementBuilder.create(it))
            }

            return
        }

        // Nothing but the type written yet — anything with a space in it has
        // moved past the type, and there is nothing left to suggest.
        if (written.contains(' ') || written.contains('$')) return

        DocTagGrammar.TYPES.forEach {
            result.withPrefixMatcher(written).addElement(
                LookupElementBuilder.create(it).withTypeText("laravel-api", true)
            )
        }
    }

    private fun completeResponse(before: String, result: CompletionResultSet, project: com.intellij.openapi.project.Project) {
        val written = before.trimStart()

        val brace = written.lastIndexOf('{')
        if (brace >= 0 && !written.substring(brace).contains('}')) {
            offerTemplates(result.withPrefixMatcher(written.substring(brace + 1)), project)

            return
        }

        // Still on the code: a bare `@response 4` gets the codes.
        if (!written.contains(' ')) {
            CODES.forEach { (code, meaning) ->
                result.withPrefixMatcher(written).addElement(
                    LookupElementBuilder.create(code).withTypeText(meaning, true)
                )
            }
        }
    }

    private fun completeSecurity(before: String, result: CompletionResultSet, project: com.intellij.openapi.project.Project) {
        val written = before.trimStart()
        if (written.contains(' ')) return

        SecurityLookup.allNames(project).forEach {
            result.withPrefixMatcher(written).addElement(
                LookupElementBuilder.create(it).withTypeText("security scheme", true)
            )
        }
    }

    private fun offerTemplates(result: CompletionResultSet, project: com.intellij.openapi.project.Project) {
        TemplateLookup.allNames(project).forEach {
            result.addElement(LookupElementBuilder.create(it).withTypeText("template", true))
        }
    }
}
