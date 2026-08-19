package dev.dskripchenko.laravelapi.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rules into tags.
 *
 * The examples are taken from printable and laravel-admin rather than invented:
 * a mapping tested on tidy cases meets none of what real controllers validate.
 */
class MarkupFromRulesTest {

    @Test
    fun `required and optional`() {
        assertEquals("string \$template", MarkupFromRules.tagFor("template", listOf("required", "string")))
        assertEquals("string ?\$version", MarkupFromRules.tagFor("version", listOf("nullable", "string")))
    }

    @Test
    fun `types the markup knows`() {
        assertEquals("integer ?\$page", MarkupFromRules.tagFor("page", listOf("nullable", "integer")))
        assertEquals("boolean ?\$caching", MarkupFromRules.tagFor("caching", listOf("nullable", "boolean")))
        assertEquals("array \$items", MarkupFromRules.tagFor("items", listOf("required", "array", "min:1")))
        assertEquals("number ?\$total", MarkupFromRules.tagFor("total", listOf("nullable", "numeric")))
    }

    @Test
    fun `formats worth carrying`() {
        assertEquals("string(email) ?\$email", MarkupFromRules.tagFor("email", listOf("nullable", "email")))
        assertEquals("string(uuid) \$uuid", MarkupFromRules.tagFor("uuid", listOf("required", "uuid")))
    }

    @Test
    fun `an enumeration goes where the generator looks for it`() {
        // At the very end, in brackets — the markup's own form.
        assertEquals("string ?\$format [link,b64]", MarkupFromRules.tagFor("format", listOf("nullable", "in:link,b64")))
    }

    @Test
    fun `a conditional requirement is optional here`() {
        // `deliver.email` is required_with:deliver. The markup cannot say
        // "required when another field is present", and pretending it is always
        // required would be a different lie from pretending it is optional —
        // the milder one is chosen, and the condition belongs in prose.
        assertEquals(
            "string(email) ?\$deliver.email",
            MarkupFromRules.tagFor("deliver.email", listOf("required_with:deliver", "email")),
        )
    }

    @Test
    fun `a list element becomes bracket notation`() {
        // `array`, not `object`: Laravel's `array` covers a list and a map
        // alike, and which one this is the rules do not say. The author may
        // refine it afterwards — the tag states what is actually known.
        assertEquals(
            "array \$items[].variables",
            MarkupFromRules.tagFor("items.*.variables", listOf("required", "array")),
        )
    }

    @Test
    fun `an element of a scalar array has no form at all`() {
        // `ids.*`, `abilities.*`. A tag written for these is dropped by the
        // generator in silence — proven in laravel-admin, where such a tag
        // produced nothing in the spec. Better to demand no documentation than
        // to demand a line that vanishes.
        assertNull(MarkupFromRules.tagFor("ids.*", listOf("required")))
        assertNull(MarkupFromRules.fieldName("abilities.*"))
    }

    @Test
    fun `an unknown rule still yields the type the generator would use`() {
        assertEquals("string ?\$whatever", MarkupFromRules.tagFor("whatever", listOf("nullable", "exists:users,id")))
    }
}
