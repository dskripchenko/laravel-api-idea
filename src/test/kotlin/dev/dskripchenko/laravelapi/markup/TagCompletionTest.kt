package dev.dskripchenko.laravelapi.markup

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Completion inside the markup.
 *
 * What is offered matters less than what is not: suggesting types where a
 * description belongs would turn every sentence into a popup, and people turn
 * such plugins off.
 */
class TagCompletionTest : BasePlatformTestCase() {

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
                        'UserResponse' => ['id' => 'integer!'],
                        'OrderRequest' => ['total' => 'number'],
                    ];
                }

                public static function getOpenApiSecurityDefinitions(): array
                {
                    return [
                        'BearerAuth' => ['type' => 'apiKey'],
                        'SignedUrlAuth' => ['type' => 'apiKey'],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    private fun suggestions(docblockLine: String): List<String> {
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
        myFixture.completeBasic()

        return myFixture.lookupElementStrings ?: emptyList()
    }

    fun `test types are offered where a type belongs`() {
        addProject()

        val offered = suggestions("""     * @input <caret>""")

        assertContainsElements(offered, "string", "integer", "boolean", "file", "object", "array", "number")
    }

    fun `test formats are offered inside the parentheses`() {
        addProject()

        val offered = suggestions("""     * @input string(<caret>""")

        assertContainsElements(offered, "date-time", "email", "uuid")
        // A format is not a type: offering both here would be noise.
        assertDoesntContain(offered, "integer")
    }

    fun `test template names are offered after an at sign`() {
        addProject()

        val offered = suggestions("""     * @input @<caret>""")

        assertContainsElements(offered, "UserResponse", "OrderRequest")
    }

    fun `test template names are offered inside the braces of a response`() {
        addProject()

        val offered = suggestions("""     * @response 200 {<caret>""")

        assertContainsElements(offered, "UserResponse", "OrderRequest")
        // Plus the two the package always provides.
        assertContainsElements(offered, "Error", "Success")
    }

    fun `test status codes are offered where the code belongs`() {
        addProject()

        val offered = suggestions("""     * @response <caret>""")

        assertContainsElements(offered, "200", "404", "422")
    }

    fun `test security schemes are offered`() {
        addProject()

        val offered = suggestions("""     * @security <caret>""")

        assertContainsElements(offered, "BearerAuth", "SignedUrlAuth")
    }

    fun `test nothing is offered where a description belongs`() {
        addProject()

        // Past the variable the rest is prose. A popup here would fire on every
        // word of every description.
        val offered = suggestions("""     * @input string ${'$'}name <caret>""")

        assertDoesntContain(offered, "string", "integer", "UserResponse")
    }

    fun `test nothing is offered without the package`() {
        val offered = suggestions("""     * @input <caret>""")

        assertDoesntContain(offered, "string", "integer")
    }
}
