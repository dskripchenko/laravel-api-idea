package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * The endpoint list.
 *
 * The window's own contents are Swing and not worth asserting on; what matters
 * is that it is registered, that it stays out of unrelated projects, and that
 * the data behind it is the whole route map rather than a subset.
 */
class EndpointsToolWindowTest : BasePlatformTestCase() {

    private fun addProject() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "app/Api/Controllers/ItemController.php",
            """
            <?php
            namespace App\Api\Controllers;
            class ItemController
            {
                public function list() {}
                public function store() {}
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "app/Api/V1.php",
            """
            <?php
            namespace App\Api;
            use Dskripchenko\LaravelApi\Components\BaseApi;
            use App\Api\Controllers\ItemController;
            class V1 extends BaseApi
            {
                public static function getMethods(): array
                {
                    return [
                        'controllers' => [
                            'item' => [
                                'controller' => ItemController::class,
                                'actions' => ['list', 'create' => ['action' => 'store']],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    /**
     * Through the extension point, not through ToolWindowManager: availability
     * is decided when the project opens, and in a fixture the package appears
     * afterwards. The same timing applies in a real IDE — a project that gains
     * laravel-api mid-session shows the window after a restart.
     */
    fun `test the window is registered with the platform`() {
        val registered = ToolWindowEP.EP_NAME.extensionList.map { it.id }

        assertTrue("the tool window is not registered", registered.contains("Laravel API"))
    }

    fun `test it is available even before the index answers`() {
        // Availability used to ask the PHP index, and the platform asks for it
        // while the project is still opening — so the index said "no package"
        // and the window was never registered. Invisible, with nothing to find.
        // It is registered unconditionally now; the panel says when a project
        // has nothing to show.
        assertTrue(EndpointsToolWindowFactory().shouldBeAvailable(project))
    }

    fun `test teaching material from vendor is not an endpoint of this application`() {
        addProject()
        // The package ships an example/ directory whose Api classes route
        // controllers named A and B. Without a filter the endpoint list of a
        // real project opened with `a.a → a()`.
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/example/Versions/v1/Api.php",
            """
            <?php
            namespace Dskripchenko\LaravelApiExample\Versions\v1;
            use Dskripchenko\LaravelApi\Components\BaseApi;
            class Api extends BaseApi
            {
                public static function getMethods(): array
                {
                    return [
                        'controllers' => [
                            'a' => [
                                'controller' => 'Dskripchenko\\LaravelApiExample\\Controllers\\AController',
                                'actions' => ['a'],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )

        val labels = RouteMapLookup.allActions(project).map { "${it.controllerKey}.${it.actionKey}" }

        assertTrue("vendor examples must not appear", labels.none { it.startsWith("a.") })
        assertEquals(setOf("item.list", "item.create"), labels.toSet())
    }

    fun `test it offers every action of the map`() {
        addProject()

        assertTrue(LaravelApiProject.isEnabled(project))

        val labels = RouteMapLookup.allActions(project).map { "${it.controllerKey}.${it.actionKey}" }

        assertEquals(setOf("item.list", "item.create"), labels.toSet())
    }
}
