package dev.dskripchenko.laravelapi.lint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading what the command prints.
 *
 * The samples are the command's real output, down to the em dash and the
 * Russian in a hint — a parser tested only on tidy fixtures meets none of that.
 */
class LintFindingTest {

    private val real = """
        {
            "errors": 2,
            "warnings": 1,
            "issues": [
                {
                    "severity": "error",
                    "rule": "action.missing-method",
                    "where": "v1 · order.export",
                    "message": "OrderController has no method 'exportOrders()' — this action answers 404, the same 404 as a mistyped URL.",
                    "hint": null
                },
                {
                    "severity": "error",
                    "rule": "response.unknown-template",
                    "where": "v1 · order.create",
                    "message": "@response 404 {MissingTemplate} refers to a template that is not defined.",
                    "hint": "Add it to getOpenApiTemplates() on the Api class."
                },
                {
                    "severity": "warning",
                    "rule": "tag.unknown-type",
                    "where": "v1 · order.show",
                    "message": "@input ${'$'}placedAt: the type `datetime` is unknown and becomes `string`.",
                    "hint": "Known types: string, file, number, integer, boolean, array, object."
                }
            ]
        }
    """.trimIndent()

    @Test
    fun `reads every finding`() {
        val findings = LintFinding.parse(real)

        assertEquals(3, findings.size)
        assertEquals("action.missing-method", findings[0].rule)
        assertTrue(findings[0].isError)
        assertTrue(!findings[2].isError)
    }

    @Test
    fun `keeps the hint, and its absence`() {
        val findings = LintFinding.parse(real)

        assertNull(findings[0].hint)
        assertEquals("Add it to getOpenApiTemplates() on the Api class.", findings[1].hint)
    }

    @Test
    fun `splits the address into the endpoint the route map knows`() {
        assertEquals("order.export", LintFinding.parse(real)[0].endpoint)
    }

    @Test
    fun `an address without a version yields no endpoint`() {
        // `api.missing-class` reports the version alone; there is nothing to
        // navigate to, and inventing one would send the reader somewhere wrong.
        val findings = LintFinding.parse(
            """{"issues":[{"severity":"error","rule":"api.missing-class","where":"v9","message":"…"}]}"""
        )

        assertNull(findings[0].endpoint)
    }

    @Test
    fun `output that is not a report yields nothing rather than an error`() {
        // What the command prints when something upstream goes wrong: a stack
        // trace, a warning from another package, an empty line.
        assertTrue(LintFinding.parse("").isEmpty())
        assertTrue(LintFinding.parse("PHP Fatal error: ...").isEmpty())
        assertTrue(LintFinding.parse("[]").isEmpty())
        assertTrue(LintFinding.parse("""{"errors":0,"warnings":0}""").isEmpty())
    }

    @Test
    fun `the report is found after whatever Laravel printed first`() {
        val noisy = "PHP Deprecated: something in a service provider\n" + real

        assertEquals(3, LintFinding.parse(LintFinding.extractJson(noisy)).size)
    }
}
