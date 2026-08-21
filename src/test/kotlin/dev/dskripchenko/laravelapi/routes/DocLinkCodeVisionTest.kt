package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.settings.LaravelApiSettings

/**
 * The line above the method, where the IDE already puts statements about a
 * declaration.
 *
 * It replaced a second gutter icon beside the route arrow, so what is checked
 * here is both that it appears and that it appears in one place only — the
 * whole point of the change was to stop saying the same thing twice on one
 * line.
 */
class DocLinkCodeVisionTest : BasePlatformTestCase() {

    private val provider = DocLinkCodeVisionProvider()

    override fun setUp() {
        super.setUp()
        LaravelApiSettings.of(project).docsBaseUrl = "https://example.test"

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
                                'controller' => \App\Controllers\TemplateController::class,
                                'actions' => [
                                    'contract' => ['action' => 'contract', 'method' => ['get']],
                                ],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    override fun tearDown() {
        try {
            LaravelApiSettings.of(project).docsBaseUrl = ""
        } finally {
            super.tearDown()
        }
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

    private fun controller() = """
        <?php
        namespace App\Controllers;

        class TemplateController
        {
            public function contract() {}

            public function helper() {}
        }
    """.trimIndent()

    private fun hints(): List<String> {
        myFixture.configureByText("TemplateController.php", controller())

        return provider.computeForEditor(myFixture.editor, myFixture.file)
            .map { (range, entry) ->
                // The tooltip is the list; the label is one word by design.
                myFixture.file.text.substring(range.startOffset, range.endOffset) + " → " + entry.tooltip
            }
    }

    fun `test the routed method gets a line, and its neighbour does not`() {
        addModule("'integration' => V1::class")

        val hints = hints()

        assertEquals("expected exactly one hint, got $hints", 1, hints.size)
        assertTrue("the hint is not on the routed method: $hints", hints.single().startsWith("contract → "))
    }

    fun `test the line is labelled API and lists what it offers`() {
        addModule("'integration' => V1::class")

        myFixture.configureByText("TemplateController.php", controller())
        val entry = provider.computeForEditor(myFixture.editor, myFixture.file).single().second

        // Short because it sits above every routed method in the file; what it
        // leads to is one click away and in the tooltip.
        assertEquals("API", (entry as com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry).text)
        assertEquals("Documentation — integration.GET", entry.tooltip)
    }

    fun `test the menu carries every export format once the application can be asked`() {
        addModule("'integration' => V1::class")
        myFixture.addFileToProject("artisan", "#!/usr/bin/env php")

        val offered = hints().single()

        // Documentation and export are separate capabilities — an address for
        // one, an application for the other — so each is offered on its own
        // terms rather than one implying the other.
        assertTrue("no documentation: $offered", offered.contains("Documentation — integration.GET"))
        for (format in dev.dskripchenko.laravelapi.export.ExportFormat.entries) {
            assertTrue("no export as ${format.label}: $offered", offered.contains("Export as ${format.label}"))
        }
    }

    fun `test the line appears for export alone, with no address to open`() {
        LaravelApiSettings.of(project).docsBaseUrl = ""
        addModule("'integration' => V1::class")
        myFixture.addFileToProject("artisan", "#!/usr/bin/env php")

        // It used to need a documentation address to appear at all, so a
        // project without APP_URL got no line and no export either.
        val offered = hints().single()

        assertFalse("documentation without an address: $offered", offered.contains("Documentation"))
        assertTrue("no export: $offered", offered.contains("Export as"))
    }

    fun `test a class the route map does not point at gets nothing`() {
        addModule("'integration' => V1::class")

        myFixture.configureByText(
            "Ordinary.php",
            """
            <?php
            namespace App\Services;

            class Ordinary
            {
                public function contract() {}
            }
            """.trimIndent()
        )

        // A method named like a routed one is not a routed one, and a project's
        // services outnumber its controllers.
        assertTrue(provider.computeForEditor(myFixture.editor, myFixture.file).isEmpty())
    }

    fun `test a version hidden from the reference index gets no line`() {
        myFixture.addFileToProject("config/laravel-api.php", "<?php\nreturn ['hidden_versions' => ['admin']];\n")
        addModule("'admin' => V1::class")

        // The page never loads that spec, so the line would promise a scroll to
        // nothing.
        assertTrue(hints().isEmpty())
    }

    fun `test nothing is shown when neither documentation nor export is possible`() {
        LaravelApiSettings.of(project).docsBaseUrl = ""
        addModule("'integration' => V1::class")

        // No address and no artisan: an empty menu is no menu.
        assertTrue(hints().isEmpty())
    }

    fun `test it sits above the declaration, where the author line sits`() {
        assertEquals(
            com.intellij.codeInsight.codeVision.CodeVisionAnchorKind.Top,
            provider.defaultAnchor,
        )
    }
}
