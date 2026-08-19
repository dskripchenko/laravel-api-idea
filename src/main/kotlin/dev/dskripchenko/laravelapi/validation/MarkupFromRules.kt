package dev.dskripchenko.laravelapi.validation

/**
 * Turns a validation rule into the tag that would document it.
 *
 * Only what the rules actually say. Laravel's vocabulary is far larger than
 * this markup's seven types, and everything unrecognised becomes `string` —
 * which is what the generator would do with it anyway, so the tag tells the
 * truth about the specification even when it says little about the field.
 */
object MarkupFromRules {

    /**
     * A field name as the markup spells it, or null when the markup cannot
     * spell it at all.
     *
     * `items.*.variables` → `items[].variables`. But `ids.*` — an element of a
     * scalar array — has no form here: a tag written for it is dropped by the
     * generator without a word. Established the hard way in laravel-admin, where
     * `@input string ?$abilities[]` produced exactly nothing in the spec.
     */
    fun fieldName(rulePath: String): String? {
        if (rulePath.endsWith(".*")) return null
        if (!rulePath.contains('*')) return rulePath

        return rulePath.replace(".*.", "[].")
    }

    /**
     * The tag body for a field: type, optionality, and whatever the rules make
     * plain about it.
     */
    fun tagFor(rulePath: String, rules: List<String>): String? {
        val name = fieldName(rulePath) ?: return null
        val optional = if (isOptional(rules)) "?" else ""
        val type = typeOf(rules)
        val enum = enumOf(rules)

        return buildString {
            append(type).append(' ').append(optional).append('$').append(name)
            if (enum != null) append(' ').append(enum)
        }
    }

    /**
     * Optional unless the rules insist otherwise.
     *
     * `required_with` and its relatives are conditional, and the markup has no
     * way to say "required when another field is present" — so such a field is
     * optional here, and the condition belongs in the description a person
     * writes.
     */
    private fun isOptional(rules: List<String>): Boolean =
        rules.none { it == "required" }

    private fun typeOf(rules: List<String>): String {
        for (rule in rules) {
            val head = rule.substringBefore(':')

            when (head) {
                "integer", "int" -> return "integer"
                "numeric", "decimal" -> return "number"
                "boolean", "bool" -> return "boolean"
                "array" -> return "array"
                "file", "image", "mimetypes", "mimes" -> return "file"
                "email" -> return "string(email)"
                "uuid" -> return "string(uuid)"
                "url", "active_url" -> return "string(uri)"
                "ip" -> return "string(ipv4)"
                "date", "date_format", "after", "before" -> return "string(date-time)"
                "json" -> return "object"
            }
        }

        return "string"
    }

    /**
     * `in:a,b,c` → `[a,b,c]`, which is how this markup writes an enumeration —
     * at the very end of the description, where the generator looks for it.
     */
    private fun enumOf(rules: List<String>): String? {
        val values = rules.firstOrNull { it.startsWith("in:") }
            ?.removePrefix("in:")
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?: return null

        return if (values.isEmpty()) null else values.joinToString(",", "[", "]")
    }
}
