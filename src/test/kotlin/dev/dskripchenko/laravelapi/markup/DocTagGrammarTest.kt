package dev.dskripchenko.laravelapi.markup

import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grammar is a copy of the PHP package's `DocPatterns`, and a copy that
 * drifts is the one thing this plugin must not become: it would paint as valid
 * exactly what the generator drops without a word.
 *
 * Every case below is taken from the package's own documentation or its test
 * fixtures.
 */
class DocTagGrammarTest {

    private fun parameter(body: String): Parsed.Parameter =
        DocTagGrammar.parse("input", body) as Parsed.Parameter

    @Test
    fun `a plain parameter`() {
        val p = parameter("string \$name Field description")

        assertEquals("string", p.type)
        assertEquals("name", p.variable)
        assertEquals("Field description", p.description)
        assertTrue(!p.optional)
        assertNull(p.format)
    }

    @Test
    fun `an optional parameter`() {
        assertTrue(parameter("integer ?\$page Page number").optional)
    }

    @Test
    fun `a type with a format`() {
        val p = parameter("string(date-time) \$createdAt When")

        assertEquals("string", p.type)
        assertEquals("date-time", p.format)
    }

    @Test
    fun `an enum at the tail of the description`() {
        val p = parameter("string \$status Status [active,blocked,pending]")

        assertEquals(listOf("active", "blocked", "pending"), p.enumValues)
        // The enum is consumed; what is left is the human description.
        assertEquals("Status", p.description)
    }

    @Test
    fun `dot notation and its parent`() {
        val p = parameter("string \$address.city City name")

        assertEquals("address.city", p.variable)
        assertEquals("address", DocTagGrammar.parentOf("address.city"))
        assertTrue(!DocTagGrammar.parentIsArray("address.city"))
    }

    @Test
    fun `array notation needs an array parent`() {
        assertEquals("tags", DocTagGrammar.parentOf("tags[].id"))
        assertTrue(DocTagGrammar.parentIsArray("tags[].id"))
    }

    @Test
    fun `a model reference`() {
        val p = DocTagGrammar.parse("input", "@OrderCreateRequest") as Parsed.ModelRef

        assertEquals("OrderCreateRequest", p.model)
        assertTrue(!p.isArray)
        assertNull(p.variable)
    }

    @Test
    fun `a model reference to an array bound to a variable`() {
        val p = DocTagGrammar.parse("output", "@User[] \$users List of users") as Parsed.ModelRef

        assertEquals("User", p.model)
        assertTrue(p.isArray)
        assertEquals("users", p.variable)
    }

    @Test
    fun `dynamic inputs from a method`() {
        val p = DocTagGrammar.parse("input", "[getOpenApiMetaInputs]") as Parsed.Callable

        assertEquals("getOpenApiMetaInputs", p.method)
    }

    @Test
    fun `a response with a template`() {
        val p = DocTagGrammar.parse("response", "200 {UserResponse}") as Parsed.Response

        assertEquals(200, p.code)
        assertEquals("UserResponse", p.template)
        assertNull(p.description)
    }

    @Test
    fun `a response with a description`() {
        val p = DocTagGrammar.parse("response", "404 Not found") as Parsed.Response

        assertEquals(404, p.code)
        assertNull(p.template)
        assertEquals("Not found", p.description)
    }

    @Test
    fun `security and default and example`() {
        assertEquals("BearerAuth", (DocTagGrammar.parse("security", "BearerAuth") as Parsed.Security).scheme)

        val d = DocTagGrammar.parse("default", "\$page 1") as Parsed.DefaultOrExample
        assertEquals("page", d.variable)
        assertEquals("1", d.value)
    }

    @Test
    fun `what the generator drops is reported as malformed`() {
        // No dollar sign: the package's own pattern requires one, and without
        // it the tag never reaches the spec.
        assertTrue(DocTagGrammar.parse("input", "string name Description") is Parsed.Malformed)
        assertTrue(DocTagGrammar.parse("response", "nope Something") is Parsed.Malformed)
        assertTrue(DocTagGrammar.parse("default", "noDollar 1") is Parsed.Malformed)

        // `@output file` — a real case from laravel-admin: a type and no
        // variable, silently dropped, leaving the export with no response
        // schema at all.
        assertTrue(DocTagGrammar.parse("output", "file") is Parsed.Malformed)
    }

    @Test
    fun `an empty tag is empty rather than malformed`() {
        assertTrue(DocTagGrammar.parse("input", "   ") is Parsed.Empty)
    }

    @Test
    fun `ranges point at the text that was passed in`() {
        val body = "  string(email) \$user.mail Address"
        val p = DocTagGrammar.parse("input", body) as Parsed.Parameter

        assertEquals("string", body.substring(p.typeRange.first, p.typeRange.last + 1))
        assertEquals("email", body.substring(p.formatRange!!.first, p.formatRange!!.last + 1))
        assertEquals("user.mail", body.substring(p.variableRange.first, p.variableRange.last + 1))
    }

    @Test
    fun `union types are not known types`() {
        // `object|null` appears in laravel-admin and silently becomes `string`.
        assertTrue(!DocTagGrammar.isKnownType("object|null"))
        assertTrue(DocTagGrammar.isKnownType("object"))
    }
}
