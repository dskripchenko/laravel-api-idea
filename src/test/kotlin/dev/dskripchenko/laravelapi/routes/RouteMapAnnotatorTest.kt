package dev.dskripchenko.laravelapi.routes

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The 404 nobody notices, caught while typing.
 */
class RouteMapAnnotatorTest : BasePlatformTestCase() {

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
                protected function hidden() {}
                public static function statically() {}
            }
            """.trimIndent()
        )
    }

    private fun check(actions: String) {
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
            $actions
                                ],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun `test an action pointing at a method that is gone`() {
        addPackageAndController()
        check(
            """                        <error descr="ItemController has no method 'methodThatWentAway()' — this action answers 404, the same 404 as a mistyped URL.">'renamed'</error> => ['action' => 'methodThatWentAway'],"""
        )
    }

    fun `test an action pointing at a method that cannot be called`() {
        addPackageAndController()
        check(
            """                        <error descr="ItemController::hidden() cannot serve an action — it has to be a public non-static method.">'secret'</error> => ['action' => 'hidden'],
                        <error descr="ItemController::statically() cannot serve an action — it has to be a public non-static method.">'static'</error> => ['action' => 'statically'],"""
        )
    }

    fun `test a working action is left alone`() {
        addPackageAndController()
        check("""                        'create' => ['action' => 'store'],""")
    }

    fun `test a disabled action is left alone`() {
        addPackageAndController()
        // `false` drops an action inherited from an earlier version; there is
        // no method to look for.
        check("""                        'gone' => false,""")
    }
}
