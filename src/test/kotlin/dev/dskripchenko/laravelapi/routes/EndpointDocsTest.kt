package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.settings.LaravelApiSettings

/**
 * From a route map entry to a link a browser can open.
 *
 * Three sources have to agree — the Api class, the module and the environment —
 * and each of the ways they can fail to is a different problem with a different
 * fix. The refusals are tested as carefully as the links.
 */
class EndpointDocsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LaravelApiSettings.of(project).docsBaseUrl = "https://example.test"
        addPackage()
    }

    override fun tearDown() {
        try {
            LaravelApiSettings.of(project).docsBaseUrl = ""
        } finally {
            super.tearDown()
        }
    }

    private fun addPackage() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseModule.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            class BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [];
                }
            }
            """.trimIndent()
        )
    }

    private fun addApi(actions: String) {
        myFixture.addFileToProject(
            "app/Api/V1.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseApi;

            class V1 extends BaseApi
            {
                public static function getMethods(): array
                {
                    return [
                        'controllers' => [
                            'template' => [
                                'controller' => \App\Api\Controllers\TemplateController::class,
                                'actions' => [$actions],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    private fun addModule(versions: String) {
        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [$versions];
                }
            }
            """.trimIndent()
        )
    }

    fun `test it builds the link the page addresses the endpoint by`() {
        addApi("'contract' => ['action' => 'contract', 'method' => ['get']]")
        addModule("'integration' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val result = EndpointDocs.of(project, entry)

        assertTrue(result is EndpointDocs.Result.Links)
        assertEquals(
            "https://example.test/api/doc#integration/tag/template/GET/integration/template/contract",
            (result as EndpointDocs.Result.Links).links.single().url,
        )
    }

    fun `test an action with no declared method is a POST, as the package routes it`() {
        addApi("'list'")
        addModule("'integration' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val links = (EndpointDocs.of(project, entry) as EndpointDocs.Result.Links).links

        assertEquals(1, links.size)
        assertTrue("expected a POST anchor: ${links.single().url}", links.single().url.contains("/POST/"))
    }

    fun `test an action answering two methods is two links`() {
        addApi("'save' => ['action' => 'save', 'method' => ['put', 'patch']]")
        addModule("'integration' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val links = (EndpointDocs.of(project, entry) as EndpointDocs.Result.Links).links

        // Two operations on the page, and picking one for the reader would be
        // picking wrong for the other half of them.
        assertEquals(listOf("PUT", "PATCH"), links.map { it.httpMethod.uppercase() })
    }

    fun `test a class exposed under two versions is two links`() {
        addApi("'contract' => ['action' => 'contract', 'method' => ['get']]")
        addModule("'v1' => V1::class, 'current' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val links = (EndpointDocs.of(project, entry) as EndpointDocs.Result.Links).links

        assertEquals(listOf("v1", "current"), links.map { it.version })
    }

    /**
     * The refusal that costs the most to get wrong: the spec of a hidden
     * version is still served, so the link looks plausible — and the page never
     * loads it, so the anchor scrolls to nothing.
     */
    fun `test a version hidden from the reference index gets no link`() {
        myFixture.addFileToProject(
            "config/laravel-api.php",
            "<?php\nreturn ['hidden_versions' => ['admin']];\n"
        )
        addApi("'list'")
        addModule("'admin' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val result = EndpointDocs.of(project, entry)

        assertTrue(result is EndpointDocs.Result.Unavailable)
        assertTrue(
            "the reason has to name the setting: ${(result as EndpointDocs.Result.Unavailable).reason}",
            result.reason.contains("hidden_versions"),
        )
    }

    fun `test a version assembled at runtime is admitted to, not invented`() {
        addApi("'list'")
        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    ${'$'}versions = [];
                    foreach (app(Registry::class)->all() as ${'$'}panel) {
                        ${'$'}versions[${'$'}panel->key()] = V1::class;
                    }

                    return ${'$'}versions;
                }
            }
            """.trimIndent()
        )

        val entry = RouteMapLookup.allActions(project).single()
        val result = EndpointDocs.of(project, entry)

        assertTrue(result is EndpointDocs.Result.Unavailable)
        assertTrue((result as EndpointDocs.Result.Unavailable).reason.contains("runtime"))
    }

    fun `test no address means the refusal names APP_URL rather than nothing`() {
        LaravelApiSettings.of(project).docsBaseUrl = ""
        addApi("'list'")
        addModule("'integration' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val result = EndpointDocs.of(project, entry)

        assertTrue(result is EndpointDocs.Result.Unavailable)
        assertTrue((result as EndpointDocs.Result.Unavailable).reason.contains("APP_URL"))
    }

    fun `test the prefix comes from the project's config`() {
        myFixture.addFileToProject("config/laravel-api.php", "<?php\nreturn ['prefix' => 'gateway'];\n")
        addApi("'list'")
        addModule("'integration' => V1::class")

        val entry = RouteMapLookup.allActions(project).single()
        val links = (EndpointDocs.of(project, entry) as EndpointDocs.Result.Links).links

        assertTrue(links.single().url.startsWith("https://example.test/gateway/doc#"))
    }
}
