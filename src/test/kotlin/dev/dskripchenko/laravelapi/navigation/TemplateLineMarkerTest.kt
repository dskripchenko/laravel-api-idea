package dev.dskripchenko.laravelapi.navigation

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The arrow from a template declaration to the docblocks naming it.
 *
 * Its absence is the useful half: a declaration with no arrow is a schema
 * nobody refers to.
 */
class TemplateLineMarkerTest : BasePlatformTestCase() {

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
            "app/Api/Controllers/UserController.php",
            """
            <?php
            namespace App\Api\Controllers;

            class UserController
            {
                /**
                 * @response 200 {UserResponse}
                 */
                public function show() {}
            }
            """.trimIndent()
        )
    }

    private fun configureTemplates() {
        myFixture.configureByText(
            "V1.php",
            """
            <?php
            namespace App\Api;
            use Dskripchenko\LaravelApi\Components\BaseApi;
            class V1 extends BaseApi
            {
                public static function getOpenApiTemplates(): array
                {
                    return [
                        'UserResponse' => ['id' => 'integer!'],
                        'NobodyUsesThis' => ['id' => 'integer!'],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    private fun tooltips(): List<String> =
        myFixture.findAllGutters().mapNotNull(GutterMark::getTooltipText)

    fun `test a referenced template carries an arrow`() {
        addProject()
        configureTemplates()

        assertTrue(tooltips().any { it.contains("referenced once") })
    }

    fun `test a template nobody refers to has none`() {
        addProject()
        configureTemplates()

        // Two declarations, one arrow: the other is the schema that can go.
        val arrows = myFixture.findAllGutters().filter { it.tooltipText?.contains("referenced") == true }
        assertEquals(1, arrows.size)
    }

    fun `test nothing appears without the package`() {
        configureTemplates()

        assertTrue(tooltips().none { it.contains("referenced") })
    }
}
