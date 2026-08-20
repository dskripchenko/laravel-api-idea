package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Reading the three settings that decide what a URL looks like.
 *
 * The trap here is treating an absent key as an absent setting. Laravel merges
 * a published config over the package's own, so a file declaring one key still
 * has all the others — and a project whose `config/laravel-api.php` overrides
 * only the documentation script is the ordinary case, not the exotic one.
 */
class ApiConfigLookupTest : BasePlatformTestCase() {

    fun `test a project with no config gets the package's defaults`() {
        val config = ApiConfigLookup.of(project)

        assertEquals("api", config.prefix)
        assertEquals("{version}/{controller}/{action}", config.uriPattern)
        assertTrue(config.hiddenVersions.isEmpty())
    }

    fun `test a config that overrides one key keeps the defaults for the rest`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            """
            <?php
            return [
                'documentation_script' => '/vendor/scalar/api-reference.js',
            ];
            """.trimIndent()
        )

        val config = ApiConfigLookup.of(project)

        // The published file is merged over the package's, so `prefix` is `api`
        // here — not absent.
        assertEquals("api", config.prefix)
        assertEquals("{version}/{controller}/{action}", config.uriPattern)
    }

    fun `test it reads a moved prefix and a reordered pattern`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            """
            <?php
            return [
                'prefix' => 'gateway',
                'uri_pattern' => '{controller}/{action}/{version}',
            ];
            """.trimIndent()
        )

        val config = ApiConfigLookup.of(project)

        assertEquals("gateway", config.prefix)
        assertEquals("{controller}/{action}/{version}", config.uriPattern)
    }

    fun `test it reads the versions kept off the reference page`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            """
            <?php
            return [
                'hidden_versions' => ['admin', 'client'],
            ];
            """.trimIndent()
        )

        val config = ApiConfigLookup.of(project)

        assertEquals(setOf("admin", "client"), config.hiddenVersions)
    }

    /**
     * The environment is not this plugin's to know. The fallback of an `env()`
     * call is what the application runs with until somebody sets the variable,
     * and that is a better answer than none.
     */
    fun `test it takes the fallback of an env call`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            """
            <?php
            return [
                'prefix' => env('LARAVEL_API_PREFIX', 'gateway'),
            ];
            """.trimIndent()
        )

        assertEquals("gateway", ApiConfigLookup.of(project).prefix)
    }

    fun `test an env call with no fallback falls back to the package's default`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            """
            <?php
            return [
                'prefix' => env('LARAVEL_API_PREFIX'),
            ];
            """.trimIndent()
        )

        assertEquals("api", ApiConfigLookup.of(project).prefix)
    }
}
