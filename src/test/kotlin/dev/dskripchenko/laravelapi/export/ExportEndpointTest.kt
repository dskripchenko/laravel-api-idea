package dev.dskripchenko.laravelapi.export

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.routes.EndpointAtCaret

/**
 * Exporting the endpoint under the caret.
 *
 * The command itself is not exercised — that needs a whole Laravel application
 * — but everything around it is: whether the action can be found, whether the
 * caret is understood, and whether a refusal says anything. "The menu item does
 * nothing" is the failure this guards against.
 */
class ExportEndpointActionTest : BasePlatformTestCase() {

    fun `test the action is registered under its id`() {
        val action = ActionManager.getInstance().getAction("LaravelApi.ExportEndpoint")

        assertNotNull("the action is not registered", action)
        assertTrue(action is ExportEndpointAction)
    }

    fun `test it sits in the editor's own menu`() {
        val menu = ActionManager.getInstance().getAction("EditorPopupMenu") as ActionGroup

        val ids = menu.getChildren(null).mapNotNull { ActionManager.getInstance().getId(it) }

        // Under Tools it would be a menu at the top of the screen asking about
        // the caret — findable, and in the wrong place.
        assertTrue("not in the editor menu", ids.contains("LaravelApi.ExportEndpoint"))
    }

    fun `test every format names an option the command accepts`() {
        // A label nobody typed into `api:export --format=` is a menu entry that
        // fails after the application has already booted.
        val known = setOf("bruno", "curl", "http", "postman", "markdown")

        assertEquals(known, ExportFormat.entries.map { it.option }.toSet())
    }

    fun `test a format is found back from the label the menu shows`() {
        for (format in ExportFormat.entries) {
            assertEquals(format, ExportFormat.byLabel(format.label))
        }

        assertNull(ExportFormat.byLabel("Something nobody offers"))
    }

    fun `test the endpoint is named the way the package names its routes`() {
        // `api.integration.template.contract` minus the prefix: what one copies
        // out of a log is what the command takes.
        assertEquals(
            "integration.template.contract",
            EndpointExporter.endpointName("integration", "template", "contract"),
        )
    }
}

/**
 * Whether the caret is somewhere an endpoint could be.
 *
 * Cheap by design — it runs whenever a menu opens — so it answers "could be",
 * and the action pays for certainty once, when invoked.
 */
class EndpointAtCaretTest : BasePlatformTestCase() {

    private fun addPackage() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
    }

    fun `test the action key in the route map is one`() {
        addPackage()

        myFixture.configureByText(
            "V1.php",
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
                                'actions' => ['con<caret>tract'],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )

        assertTrue(EndpointAtCaret.looksLikeOne(myFixture.file, myFixture.caretOffset))
    }

    fun `test a controller method is one`() {
        myFixture.configureByText(
            "TemplateController.php",
            """
            <?php
            namespace App\Controllers;

            class TemplateController
            {
                public function con<caret>tract() {}
            }
            """.trimIndent()
        )

        assertTrue(EndpointAtCaret.looksLikeOne(myFixture.file, myFixture.caretOffset))
    }

    fun `test a string in a class that is not an Api is not one`() {
        addPackage()

        myFixture.configureByText(
            "Ordinary.php",
            """
            <?php
            namespace App;

            class Ordinary
            {
                public function run(): array
                {
                    return ['some<caret>thing'];
                }
            }
            """.trimIndent()
        )

        // Otherwise the menu item would appear over every quoted string in the
        // project and be wrong nearly every time.
        assertFalse(EndpointAtCaret.looksLikeOne(myFixture.file, myFixture.caretOffset))
    }
}
