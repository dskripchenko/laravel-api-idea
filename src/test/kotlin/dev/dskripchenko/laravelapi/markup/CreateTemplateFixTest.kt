package dev.dskripchenko.laravelapi.markup

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.inspections.UnknownTemplateInspection

/**
 * Alt+Enter on a template nobody declared.
 *
 * The check is on the file that gets edited, not on the popup: what matters is
 * that the name lands spelled exactly as it is written in the docblock, in the
 * right array, without disturbing what was already there.
 */
class CreateTemplateFixTest : BasePlatformTestCase() {

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

    private fun addApiClass(body: String) {
        myFixture.addFileToProject(
            "app/Api/V1.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseApi;

            class V1 extends BaseApi
            {
            $body
            }
            """.trimIndent()
        )
    }

    private fun applyFixOn(docblockLine: String) {
        // The fix now arrives from an inspection, not from an annotator: without
        // enabling it there is nothing to offer.
        myFixture.enableInspections(UnknownTemplateInspection::class.java)
        myFixture.configureByText(
            "UserController.php",
            """
            <?php
            class UserController
            {
                /**
            $docblockLine
                 */
                public function create() {}
            }
            """.trimIndent()
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Declare template") }
        assertNotNull("the fix should be offered on the error", fix)
        myFixture.launchAction(fix!!)
    }

    /**
     * Through PSI rather than the file on disk: the fix edits the document, and
     * the disk copy is only written later — reading it would test the flush,
     * not the fix.
     */
    private fun apiFileText(): String {
        val virtualFile = myFixture.findFileInTempDir("app/Api/V1.php")

        return com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)!!.text
    }

    fun `test it declares the template next to the existing ones`() {
        addPackage()
        addApiClass(
            """
                public static function getOpenApiTemplates(): array
                {
                    return [
                        'UserResponse' => ['id' => 'integer!'],
                    ];
                }
            """.trimIndent()
        )

        applyFixOn("""     * @response 404 {MissingTemplate}""")

        val text = apiFileText()
        assertTrue("the new template is declared", text.contains("'MissingTemplate' => []"))
        assertTrue("the existing one survives", text.contains("'UserResponse' => ['id' => 'integer!']"))
    }

    fun `test it declares the template into an empty array`() {
        addPackage()
        addApiClass(
            """
                public static function getOpenApiTemplates(): array
                {
                    return [];
                }
            """.trimIndent()
        )

        applyFixOn("""     * @input @OrderRequest""")

        assertTrue(apiFileText().contains("'OrderRequest' => []"))
    }

    fun `test it writes the whole method when there is none`() {
        addPackage()
        addApiClass("""    // nothing yet""")

        applyFixOn("""     * @response 200 {FirstTemplate}""")

        val text = apiFileText()
        assertTrue("the method appears", text.contains("function getOpenApiTemplates"))
        assertTrue("with the template in it", text.contains("'FirstTemplate' => []"))
    }

    fun `test the name is taken verbatim from the docblock`() {
        addPackage()
        addApiClass(
            """
                public static function getOpenApiTemplates(): array
                {
                    return [];
                }
            """.trimIndent()
        )

        // A second, slightly different spelling is the whole failure mode this
        // fix removes.
        applyFixOn("""     * @output @Order_Item2[] ${'$'}items Items""")

        assertTrue(apiFileText().contains("'Order_Item2' => []"))
    }
}
