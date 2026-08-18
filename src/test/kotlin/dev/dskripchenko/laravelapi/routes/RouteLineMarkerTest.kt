package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The gutter arrows, checked through the same machinery the editor uses.
 *
 * The absence of an arrow is as much a result as its presence: an action whose
 * method was renamed away gets none, and that is the earliest visible sign of
 * an endpoint that will answer 404.
 */
class RouteLineMarkerTest : BasePlatformTestCase() {

    private fun addPackageAndController() {
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
                public function store() {}
                public function orphan() {}
            }
            """.trimIndent()
        )
    }

    private fun gutterTooltips(): List<String> =
        myFixture.findAllGutters().mapNotNull(GutterMark::getTooltipText)

    fun `test the map shows an arrow to the method it routes`() {
        addPackageAndController()
        myFixture.configureByText(
            "V1.php",
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
                                    'create' => ['action' => 'store'],
                                ],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )

        assertTrue(
            "the action should point at the method it routes",
            gutterTooltips().any { it.contains("item.create") && it.contains("store()") }
        )
    }

    fun `test an action pointing at a renamed method gets no arrow`() {
        addPackageAndController()
        myFixture.configureByText(
            "V1.php",
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
                                    'create' => ['action' => 'methodThatWentAway'],
                                ],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )

        assertTrue(
            "nothing to point at, so no arrow",
            gutterTooltips().none { it.contains("item.create") }
        )
    }

    fun `test the method shows an arrow back to the map`() {
        addPackageAndController()
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
                                'actions' => ['create' => ['action' => 'store']],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "ItemController.php",
            """
            <?php
            namespace App\Api\Controllers;

            class ItemController
            {
                public function store() {}
                public function orphan() {}
            }
            """.trimIndent()
        )

        val tooltips = gutterTooltips()
        assertTrue("store() is routed and should say so", tooltips.any { it.contains("routed as item.create") })
        assertTrue("orphan() is routed by nothing", tooltips.none { it.contains("orphan") })
    }

    fun `test nothing appears without the package`() {
        myFixture.addFileToProject(
            "app/Api/Controllers/ItemController.php",
            """
            <?php
            namespace App\Api\Controllers;
            class ItemController { public function store() {} }
            """.trimIndent()
        )
        myFixture.configureByText(
            "V1.php",
            """
            <?php
            class V1
            {
                public static function getMethods(): array
                {
                    return ['controllers' => ['item' => ['actions' => ['create' => 'store']]]];
                }
            }
            """.trimIndent()
        )

        assertTrue(gutterTooltips().none { it.contains("item.create") })
    }
}
