package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.PhpIndex

/**
 * Reading the route map.
 *
 * All four ways of writing an action are exercised on purpose: a reader that
 * understood three of them would silently ignore the fourth, and silence is the
 * failure mode this whole plugin exists to remove.
 */
class RouteMapLookupTest : BasePlatformTestCase() {

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
                public function getById() {}
                public function store() {}
                public function update() {}
                public function gone() {}
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
                                'actions' => [
                                    'list',
                                    'show' => 'getById',
                                    'disabled' => false,
                                    'create' => ['action' => 'store', 'method' => ['post']],
                                    'update' => ['method' => ['post']],
                                    'renamed' => ['action' => 'methodThatWentAway'],
                                ],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    private fun actions() = RouteMapLookup.allActions(project).associateBy { it.actionKey }

    fun `test a bare value is both the action and the method`() {
        addProject()

        val entry = actions()["list"]!!
        assertEquals("list", entry.methodName)
        assertEquals("item", entry.controllerKey)
        assertEquals("\\App\\Api\\Controllers\\ItemController", entry.controllerFqn)
    }

    fun `test a string value is an alias for the method`() {
        addProject()

        assertEquals("getById", actions()["show"]!!.methodName)
    }

    fun `test an explicit action key wins`() {
        addProject()

        assertEquals("store", actions()["create"]!!.methodName)
    }

    fun `test an array without an action key falls back to the key`() {
        addProject()

        assertEquals("update", actions()["update"]!!.methodName)
    }

    fun `test a disabled action is not an endpoint`() {
        addProject()

        // `'disabled' => false` turns off an action inherited from an earlier
        // version. Treating it as routable would invent an endpoint.
        assertNull(actions()["disabled"])
    }

    fun `test an entry resolves to its controller method`() {
        addProject()

        val target = RouteMapLookup.targetMethod(project, actions()["create"]!!)

        assertNotNull(target)
        assertEquals("store", target!!.name)
    }

    fun `test an entry pointing at a renamed method resolves to nothing`() {
        addProject()

        // The defect the whole plugin is for: at runtime this is a 404
        // indistinguishable from a wrong URL.
        assertNull(RouteMapLookup.targetMethod(project, actions()["renamed"]!!))
    }

    fun `test the way back from a method to its map entries`() {
        addProject()

        val controller = PhpIndex.getInstance(project)
            .getClassesByFQN("\\App\\Api\\Controllers\\ItemController")
            .first()
        val entries = RouteMapLookup.entriesFor(controller.findMethodByName("getById")!!)

        assertEquals(1, entries.size)
        assertEquals("show", entries.first().actionKey)
    }

    fun `test a method nothing routes has no entries`() {
        addProject()

        val controller = PhpIndex.getInstance(project)
            .getClassesByFQN("\\App\\Api\\Controllers\\ItemController")
            .first()

        assertTrue(RouteMapLookup.entriesFor(controller.findMethodByName("gone")!!).isEmpty())
    }
}
