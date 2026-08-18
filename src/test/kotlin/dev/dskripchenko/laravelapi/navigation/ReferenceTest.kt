package dev.dskripchenko.laravelapi.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Ctrl+Click inside the markup.
 *
 * Three jumps, in the order they cost a person time:
 *   `@response 200 {UserResponse}` → the key in getOpenApiTemplates()
 *   `@input @OrderRequest`         → the same
 *   `@input [buildInputs]`         → the controller's own method
 *
 * Today all three are grey text: the only way to check that a template exists
 * is to open the Api class and read an array by eye.
 */
class ReferenceTest : BasePlatformTestCase() {

    private fun enableLaravelApi() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
    }

    private fun addApiClass() {
        myFixture.addFileToProject(
            "app/Api/V1.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseApi;

            class V1 extends BaseApi
            {
                public static function getOpenApiTemplates(): array
                {
                    return [
                        'UserResponse' => [
                            'id' => 'integer!',
                        ],
                        'OrderRequest' => [
                            'total' => 'number',
                        ],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    /**
     * Where Ctrl+Click actually lands.
     *
     * Through the platform's own action rather than by calling the handler
     * directly. The direct call is what the first version of this test did, and
     * it passed while navigation was dead in the IDE: the handler was
     * registered under `codeInsight.gotoDeclarationHandler`, an extension point
     * that does not exist, so nothing ever invoked it. A test that skips the
     * dispatch cannot see that.
     */
    private fun resolveAtCaret(): PsiElement? =
        GotoDeclarationAction.findTargetElement(project, myFixture.editor, myFixture.caretOffset)

    private fun configureController(docblock: String) {
        myFixture.configureByText(
            "UserController.php",
            """
            <?php
            namespace App\Api\Controllers;

            class UserController
            {
                /**
            $docblock
                 */
                public function create() {}

                public function buildInputs(): array { return []; }
            }
            """.trimIndent()
        )
    }

    fun `test a response template resolves to its definition`() {
        enableLaravelApi()
        addApiClass()
        configureController("""     * @response 200 {User<caret>Response}""")

        val target = resolveAtCaret()

        assertNotNull("{UserResponse} should resolve", target)
        assertEquals("UserResponse", (target as StringLiteralExpression).contents)
    }

    fun `test a model reference resolves to its definition`() {
        enableLaravelApi()
        addApiClass()
        configureController("""     * @input @Order<caret>Request""")

        val target = resolveAtCaret()

        assertNotNull("@OrderRequest should resolve", target)
        assertEquals("OrderRequest", (target as StringLiteralExpression).contents)
    }

    fun `test dynamic inputs resolve to the controller's own method`() {
        enableLaravelApi()
        addApiClass()
        configureController("""     * @input [build<caret>Inputs]""")

        val target = resolveAtCaret()

        assertNotNull("[buildInputs] should resolve", target)
        assertEquals("buildInputs", (target as Method).name)
    }

    fun `test a template nobody defined resolves to nothing`() {
        enableLaravelApi()
        addApiClass()
        configureController("""     * @response 404 {Miss<caret>ingTemplate}""")

        // Resolving to nothing is what makes the reference red, and red is the
        // whole point: the generator would put a $ref here pointing nowhere.
        assertNull(resolveAtCaret())
    }

    fun `test nothing resolves without the package`() {
        addApiClass()
        configureController("""     * @response 200 {User<caret>Response}""")

        assertNull(resolveAtCaret())
    }
}
