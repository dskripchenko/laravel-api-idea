package dev.dskripchenko.laravelapi.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Where the documentation is served from — the one part of a link that the
 * source cannot answer.
 *
 * Every refusal here is checked for saying what is missing. A link that
 * silently does nothing is the failure this whole feature is meant to avoid.
 */
class DocsBaseUrlTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            LaravelApiSettings.of(project).docsBaseUrl = ""
        } finally {
            super.tearDown()
        }
    }

    fun `test it reads APP_URL from the project's env`() {
        myFixture.addFileToProject(".env", "APP_NAME=Example\nAPP_URL=https://example.test\nDB_HOST=127.0.0.1\n")

        val result = DocsBaseUrl.of(project)

        assertTrue(result is DocsBaseUrl.Result.Found)
        assertEquals("https://example.test", (result as DocsBaseUrl.Result.Found).url)
        assertFalse("nobody configured this — it was read", result.configured)
    }

    fun `test quotes and a trailing slash are not part of the address`() {
        myFixture.addFileToProject(".env", "APP_URL=\"https://example.test/\"\n")

        assertEquals("https://example.test", (DocsBaseUrl.of(project) as DocsBaseUrl.Result.Found).url)
    }

    fun `test a key set twice is settled by the last line, as any reader settles it`() {
        myFixture.addFileToProject(".env", "APP_URL=https://first.test\nAPP_URL=https://second.test\n")

        assertEquals("https://second.test", (DocsBaseUrl.of(project) as DocsBaseUrl.Result.Found).url)
    }

    fun `test a commented out line is not a setting`() {
        myFixture.addFileToProject(".env", "# APP_URL=https://commented.test\nAPP_URL=https://real.test\n")

        assertEquals("https://real.test", (DocsBaseUrl.of(project) as DocsBaseUrl.Result.Found).url)
    }

    fun `test what is configured wins over the env`() {
        myFixture.addFileToProject(".env", "APP_URL=http://localhost\n")
        LaravelApiSettings.of(project).docsBaseUrl = "https://stand.example.test"

        val result = DocsBaseUrl.of(project) as DocsBaseUrl.Result.Found

        // Reading the documentation of a stand while working on a local
        // checkout is an ordinary thing to want, and nothing in the repository
        // can express it.
        assertEquals("https://stand.example.test", result.url)
        assertTrue(result.configured)
    }

    fun `test a project with no env says so instead of guessing`() {
        val result = DocsBaseUrl.of(project)

        assertTrue(result is DocsBaseUrl.Result.Missing)
        assertTrue(
            "the reason has to name what is missing",
            (result as DocsBaseUrl.Result.Missing).reason.contains("APP_URL"),
        )
    }

    fun `test an address without a scheme is refused by name`() {
        LaravelApiSettings.of(project).docsBaseUrl = "example.test"

        val result = DocsBaseUrl.of(project)

        assertTrue(result is DocsBaseUrl.Result.Missing)
        assertTrue((result as DocsBaseUrl.Result.Missing).reason.contains("example.test"))
    }
}
