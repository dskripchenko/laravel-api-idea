package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The anchor format, pinned on this side of the line.
 *
 * These are the same URLs the package's own `DocLinkTest` names. That is the
 * whole safety net behind reimplementing thirty lines that already exist in
 * PHP: two suites asserting one format fail together when the format moves,
 * instead of one of them quietly building links to nowhere.
 */
class DocLinkTest : BasePlatformTestCase() {

    private val defaults = ApiConfigLookup.Config(
        prefix = ApiConfigLookup.DEFAULT_PREFIX,
        uriPattern = ApiConfigLookup.DEFAULT_URI_PATTERN,
        hiddenVersions = emptySet(),
    )

    fun `test it builds the anchor the page addresses an endpoint by`() {
        // Verbatim from a live reference page, with the version now passed to
        // it as an explicit slug.
        assertEquals(
            "integration/tag/template/GET/integration/template/contract",
            DocLink.anchor(ApiConfigLookup.DEFAULT_URI_PATTERN, "integration", "template", "contract", "get"),
        )
    }

    fun `test it uppercases the method`() {
        assertEquals(
            "v1/tag/user/POST/v1/user/list",
            DocLink.anchor(ApiConfigLookup.DEFAULT_URI_PATTERN, "v1", "user", "list", "post"),
        )
    }

    fun `test it follows the URI pattern rather than assuming one`() {
        assertEquals("/user/list/v1", DocLink.path("{controller}/{action}/{version}", "v1", "user", "list"))
    }

    fun `test it slugifies the way the page does`() {
        assertEquals("the-integration-api", DocLink.slug("The integration API."))
        assertEquals("order-items", DocLink.slug("  Order  items "))
        assertEquals("print-form", DocLink.slug("print_form"))
        assertEquals("edge", DocLink.slug("--edge--"))
    }

    fun `test it keeps unicode letters instead of collapsing a name into nothing`() {
        assertEquals("печать-документов", DocLink.slug("Печать документов"))
    }

    fun `test the slug is idempotent`() {
        // The page slugifies whatever it is handed. A slug that changed on the
        // second pass would address a section that does not exist.
        for (text in listOf("The integration API.", "v1.1", "print_form", "Печать")) {
            val once = DocLink.slug(text)

            assertEquals(once, DocLink.slug(once))
        }
    }

    fun `test a dotted version stays readable`() {
        assertEquals("v1-1", DocLink.documentSlug("v1.1"))
        assertEquals("v1-1", DocLink.slug(DocLink.documentSlug("v1.1")))
    }

    fun `test the URL carries the project's own prefix`() {
        val config = defaults.copy(prefix = "gateway")

        assertEquals(
            "https://example.test/gateway/doc#v1/tag/user/GET/v1/user/list",
            DocLink.url("https://example.test/", config, "v1", "user", "list", "get"),
        )
    }

    fun `test an empty prefix does not leave a double slash`() {
        val config = defaults.copy(prefix = "")

        assertEquals(
            "https://example.test/doc#v1/tag/user/GET/v1/user/list",
            DocLink.url("https://example.test", config, "v1", "user", "list", "get"),
        )
    }
}
