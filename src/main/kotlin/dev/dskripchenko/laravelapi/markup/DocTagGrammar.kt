package dev.dskripchenko.laravelapi.markup

/**
 * The grammar of the docblock markup, mirrored from the PHP package.
 *
 * The source of truth is `Dskripchenko\LaravelApi\Services\OpenApi\DocPatterns`
 * — the same expressions the OpenAPI generator and `api:lint` read the markup
 * with. Keeping a copy here is unavoidable: the IDE cannot run the project's
 * PHP. Keeping it *faithful* is not optional, and that is what the tests pin
 * down: a plugin that highlights as valid what the generator silently drops
 * would be worse than no plugin, because it would give the mistake a green
 * light.
 */
object DocTagGrammar {

    /** The tags this plugin understands. Anything else is somebody else's. */
    val TAGS = setOf("input", "output", "header", "response", "security", "default", "example")

    /** The types the generator recognises. Everything else becomes `string`. */
    val TYPES = listOf("string", "file", "number", "integer", "boolean", "array", "object")

    /** `type(format) ?$variable Description` — @input, @output, @header. */
    private val PARAMETER =
        Regex("""^(?<type>\S*?)(?:\((?<format>[a-zA-Z0-9\-]+)\))?\s*(?<optional>\?)?\$(?<variable>\S*)(?:\s*(?<description>\S[\s\S]*?))?$""")

    /** `@Model`, `@Model[]`, optionally bound to a variable. */
    private val MODEL_REF =
        Regex("""^@(?<model>\w+)(?<isArray>\[])?\s*(?:(?<optional>\?)?\$(?<variable>\S+)(?:\s+(?<description>.+))?)?$""")

    /** `{Template} Description`. */
    private val TEMPLATE_REF = Regex("""^\{(?<template>\S*?)}(?<description>[\s\S]*)$""")

    /** `[methodName]` — inputs the controller assembles at runtime. */
    private val CALLABLE = Regex("""^\[(?<callable>\S*?)]$""")

    /** `404 {Template}` or `404 Description`. */
    private val RESPONSE = Regex("""^(?<code>\d{3})\s+(?:\{(?<template>\S*?)}|(?<description>.+))$""")

    /** `$variable value` — @default and @example. */
    private val DEFAULT_EXAMPLE = Regex("""^\$(?<variable>\S+)\s+(?<value>.+)$""")

    /** An enum, written at the very end of a description: `[a,b,c]`. */
    private val ENUM = Regex("""\[(?<values>[^\[\]]+)]\s*$""")

    /**
     * What a tag body turned out to be.
     *
     * Ranges are offsets inside the body, not the file: whoever holds a PSI
     * element knows where the body starts and can shift them. Keeping the
     * parser ignorant of PSI is what lets it be tested without an IDE.
     */
    sealed interface Parsed {
        data class Parameter(
            val type: String,
            val typeRange: IntRange,
            val format: String?,
            val formatRange: IntRange?,
            val optional: Boolean,
            val variable: String,
            val variableRange: IntRange,
            val description: String?,
            val enumValues: List<String>?,
        ) : Parsed

        data class ModelRef(
            val model: String,
            val modelRange: IntRange,
            val isArray: Boolean,
            val variable: String?,
        ) : Parsed

        data class TemplateRef(
            val template: String,
            val templateRange: IntRange,
        ) : Parsed

        data class Callable(
            val method: String,
            val methodRange: IntRange,
        ) : Parsed

        data class Response(
            val code: Int,
            val codeRange: IntRange,
            val template: String?,
            val templateRange: IntRange?,
            val description: String?,
        ) : Parsed

        data class Security(
            val scheme: String,
            val schemeRange: IntRange,
        ) : Parsed

        data class DefaultOrExample(
            val variable: String,
            val variableRange: IntRange,
            val value: String,
        ) : Parsed

        /** The body is present and does not parse — the generator drops it. */
        data class Malformed(val reason: String) : Parsed

        /** The tag carries nothing at all. */
        data object Empty : Parsed
    }

    fun parse(tagName: String, rawBody: String): Parsed {
        val body = rawBody.trim()
        if (body.isEmpty()) return Parsed.Empty

        // The offset of the trimmed body inside the raw one, so the ranges a
        // caller gets point at the real text and not at a phantom shifted left.
        val shift = rawBody.indexOf(body).coerceAtLeast(0)

        return when (tagName) {
            "input", "output", "header" -> parseParameterLike(body, shift)
            "response" -> parseResponse(body, shift)
            "security" -> Parsed.Security(body, shift until shift + body.length)
            "default", "example" -> parseDefaultOrExample(body, shift)
            else -> Parsed.Malformed("unknown tag")
        }
    }

    private fun parseParameterLike(body: String, shift: Int): Parsed {
        CALLABLE.matchEntire(body)?.let { m ->
            val g = m.groups["callable"]!!
            return Parsed.Callable(g.value, g.range.shiftedBy(shift))
        }

        if (body.startsWith("@")) {
            val m = MODEL_REF.matchEntire(body)
                ?: return Parsed.Malformed("expected @Model, @Model[] or @Model \$variable")
            val model = m.groups["model"]!!
            return Parsed.ModelRef(
                model = model.value,
                modelRange = model.range.shiftedBy(shift),
                isArray = m.groups["isArray"] != null,
                variable = m.groups["variable"]?.value,
            )
        }

        if (body.startsWith("{")) {
            val m = TEMPLATE_REF.matchEntire(body)
                ?: return Parsed.Malformed("expected {TemplateName}")
            val template = m.groups["template"]!!
            return Parsed.TemplateRef(template.value, template.range.shiftedBy(shift))
        }

        val m = PARAMETER.matchEntire(body)
            ?: return Parsed.Malformed("expected: type ?\$name Description")

        val type = m.groups["type"]!!
        val variable = m.groups["variable"]!!
        val format = m.groups["format"]
        var description = m.groups["description"]?.value

        // `Status [active,blocked]` — the enum lives at the tail of the
        // description, and the description keeps whatever is left of it.
        var enumValues: List<String>? = null
        if (description != null) {
            ENUM.find(description)?.let { e ->
                enumValues = e.groups["values"]!!.value.split(',').map(String::trim).filter(String::isNotEmpty)
                description = description!!.removeRange(e.range).trim().ifEmpty { null }
            }
        }

        return Parsed.Parameter(
            type = type.value,
            typeRange = type.range.shiftedBy(shift),
            format = format?.value,
            formatRange = format?.range?.shiftedBy(shift),
            optional = m.groups["optional"] != null,
            variable = variable.value,
            variableRange = variable.range.shiftedBy(shift),
            description = description,
            enumValues = enumValues,
        )
    }

    private fun parseResponse(body: String, shift: Int): Parsed {
        val m = RESPONSE.matchEntire(body)
            ?: return Parsed.Malformed("expected: 200 {Template} or 404 Description")

        val code = m.groups["code"]!!
        val template = m.groups["template"]

        return Parsed.Response(
            code = code.value.toInt(),
            codeRange = code.range.shiftedBy(shift),
            template = template?.value,
            templateRange = template?.range?.shiftedBy(shift),
            description = m.groups["description"]?.value,
        )
    }

    private fun parseDefaultOrExample(body: String, shift: Int): Parsed {
        val m = DEFAULT_EXAMPLE.matchEntire(body)
            ?: return Parsed.Malformed("expected: \$variable value")

        val variable = m.groups["variable"]!!
        return Parsed.DefaultOrExample(
            variable = variable.value,
            variableRange = variable.range.shiftedBy(shift),
            value = m.groups["value"]!!.value,
        )
    }

    fun isKnownType(type: String): Boolean = type in TYPES

    /**
     * The parent a dotted variable needs, or null when it needs none.
     * `address.city` → `address`; `tags[].id` → `tags`.
     */
    fun parentOf(variable: String): String? {
        val dot = variable.lastIndexOf('.')
        if (dot <= 0) return null

        return variable.substring(0, dot).removeSuffix("[]")
    }

    /** Whether the parent of a dotted variable has to be an array. */
    fun parentIsArray(variable: String): Boolean {
        val dot = variable.lastIndexOf('.')
        if (dot <= 0) return false

        return variable.substring(0, dot).endsWith("[]")
    }
}

private fun IntRange.shiftedBy(offset: Int): IntRange =
    (first + offset)..(last + offset)
