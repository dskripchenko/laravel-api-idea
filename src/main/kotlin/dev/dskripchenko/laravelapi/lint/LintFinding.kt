package dev.dskripchenko.laravelapi.lint

import com.google.gson.JsonParser

/**
 * One finding as `api:lint --json` reports it.
 *
 * The shape is the command's, not ours: `severity`, `rule`, `where`, `message`,
 * `hint`. Mirroring it rather than inventing a richer model is deliberate — the
 * command owns the rules, and a plugin that reshaped its output would start
 * disagreeing with it the first time a rule changed.
 */
data class LintFinding(
    val severity: String,
    val rule: String,
    val where: String,
    val message: String,
    val hint: String?,
) {
    val isError: Boolean get() = severity == "error"

    /** `v1 · order.create` → `order.create`, the part the route map knows. */
    val endpoint: String? get() = where.substringAfter(" · ", "").takeIf { it.isNotEmpty() }

    companion object {

        /**
         * Reads the command's JSON.
         *
         * Anything unreadable comes back as an empty list rather than an
         * exception: the output may be a stack trace, a warning printed by some
         * other package's service provider, or nothing at all, and none of that
         * should surface as a plugin error.
         */
        fun parse(json: String): List<LintFinding> {
            val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptyList()
            if (!root.isJsonObject) return emptyList()

            val issues = root.asJsonObject.get("issues")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return emptyList()

            return issues.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val issue = element.asJsonObject

                LintFinding(
                    severity = issue.get("severity")?.asStringOrNull() ?: return@mapNotNull null,
                    rule = issue.get("rule")?.asStringOrNull().orEmpty(),
                    where = issue.get("where")?.asStringOrNull().orEmpty(),
                    message = issue.get("message")?.asStringOrNull().orEmpty(),
                    hint = issue.get("hint")?.asStringOrNull(),
                )
            }
        }

        /**
         * The command prints its report on stdout, and Laravel is free to print
         * other things first — a deprecation, a warning from a service
         * provider. The JSON is what starts at the first brace.
         */
        fun extractJson(output: String): String {
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')

            return if (start >= 0 && end > start) output.substring(start, end + 1) else ""
        }
    }
}

private fun com.google.gson.JsonElement.asStringOrNull(): String? =
    if (isJsonPrimitive) asString else null
