package dev.dskripchenko.laravelapi.inspections

import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Writing the method an action points at.
 *
 * What matters is not that a method appears but what it looks like: the name
 * has to match the map exactly, the neighbours' conventions have to be kept,
 * and nothing may be invented that the author has not decided.
 */
class CreateActionMethodFixTest : BasePlatformTestCase() {

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

    private fun addController(body: String) {
        myFixture.addFileToProject(
            "app/Api/Controllers/ItemController.php",
            """
            <?php
            namespace App\Api\Controllers;

            class ItemController
            {
                public function success(${'$'}payload = []) { return ${'$'}payload; }
            $body
            }
            """.trimIndent()
        )
    }

    private fun applyFix(action: String, method: String) {
        myFixture.enableInspections(RouteMapInspection::class.java)
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
                                'actions' => ['$action' => ['action' => '$method']],
                            ],
                        ],
                    ];
                }
            }
            """.trimIndent()
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Create method") }
        assertNotNull("the fix should be offered on a missing method", fix)
        myFixture.launchAction(fix!!)
    }

    /**
     * The docblock of the method that was just created.
     *
     * Sliced by locating that method rather than by splitting on a neighbour's
     * text: reformatting moves the neighbours around, and the first version of
     * this test compared the whole file against itself without noticing.
     */
    private fun createdDocblock(method: String): String {
        val text = controllerText()
        val at = text.indexOf("public function $method(")
        assertTrue("the method was not created", at > 0)

        val docStart = text.lastIndexOf("/**", at)

        return if (docStart in 0 until at) text.substring(docStart, at) else ""
    }

    private fun controllerText(): String =
        PsiManager.getInstance(project)
            .findFile(myFixture.findFileInTempDir("app/Api/Controllers/ItemController.php"))!!
            .text

    fun `test it writes the method the map names`() {
        addPackage()
        addController("""    public function list() {}""")

        applyFix(action = "create", method = "store")

        val text = controllerText()
        assertTrue("the method is created", text.contains("public function store()"))
        // Through the controller's own helper, because it has one.
        assertTrue("returns through success()", text.contains("return \$this->success();"))
    }

    fun `test it keeps the security the neighbours agree on`() {
        addPackage()
        addController(
            """
                /**
                 * List them
                 *
                 * @security AdminSession
                 */
                public function list() {}

                /**
                 * Show one
                 *
                 * @security AdminSession
                 */
                public function show() {}
            """.trimIndent()
        )

        applyFix(action = "create", method = "store")

        // Authentication belongs to the controller, not to one action.
        assertTrue(createdDocblock("store").contains("@security AdminSession"))
    }

    fun `test it invents no security where the neighbours disagree`() {
        addPackage()
        addController(
            """
                /** @security AdminSession */
                public function list() {}

                /** @security AdminBearer */
                public function show() {}
            """.trimIndent()
        )

        applyFix(action = "create", method = "store")

        // Which scheme a new action needs is a decision; guessing is worse than
        // leaving the line out.
        assertFalse(createdDocblock("store").contains("@security"))
    }

    fun `test it borrows no response template`() {
        addPackage()
        addController(
            """
                /**
                 * @security AdminSession
                 * @response 200 {ItemListResponse}
                 */
                public function list() {}
            """.trimIndent()
        )

        applyFix(action = "create", method = "store")

        // A template names the body of *that* answer. Copying it would document
        // a response this method does not return — and would look deliberate.
        assertFalse(createdDocblock("store").contains("@response"))
    }
}
