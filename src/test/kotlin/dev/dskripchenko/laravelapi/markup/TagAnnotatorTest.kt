package dev.dskripchenko.laravelapi.markup

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The annotator against real PSI, because the grammar being right proves
 * nothing about the plugin firing at all: the tag has to reach it as a
 * PhpDocTag, and the offsets have to land on the right characters.
 */
class TagAnnotatorTest : BasePlatformTestCase() {

    /**
     * The plugin stays quiet in projects that do not use the package — `@input`
     * and `@output` are ordinary words. The stub is what switches it on.
     */
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

    /** An Api class declaring the templates the markup below refers to. */
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
                        'User' => ['id' => 'integer!'],
                        'UserResponse' => ['id' => 'integer!'],
                    ];
                }

                public static function getOpenApiSecurityDefinitions(): array
                {
                    return ['BearerAuth' => ['type' => 'apiKey']];
                }
            }
            """.trimIndent()
        )
    }

    private fun check(docblock: String) {
        myFixture.configureByText(
            "Controller.php",
            """
            <?php
            class UserController
            {
                /**
            $docblock
                 */
                public function create() {}
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, false)
    }

    fun `test markup that does not parse is reported`() {
        enableLaravelApi()
        check(
            """     * @input <error descr="@input does not parse and will be ignored — expected: type ?${'$'}name Description.">string name Description</error>"""
        )
    }

    fun `test an unknown type is a warning that names the consequence`() {
        enableLaravelApi()
        check(
            """     * @input <warning descr="Unknown type 'datetime' — the generator will call it 'string'. Known types: string, file, number, integer, boolean, array, object.">datetime</warning> ${'$'}when When"""
        )
    }

    fun `test an impossible status code is an error`() {
        enableLaravelApi()
        check(
            """     * @response <error descr="999 is not an HTTP status code.">999</error> Impossible"""
        )
    }

    fun `test valid markup is left alone`() {
        enableLaravelApi()
        addApiClass()
        check(
            """
     * @input string(email) ${'$'}email Address
     * @input integer ?${'$'}page Page [1,2,3]
     * @output @User[] ${'$'}users Users
     * @response 200 {UserResponse}
     * @security BearerAuth
            """.trimIndent()
        )
    }

    fun `test a template nobody declared is an error`() {
        enableLaravelApi()
        addApiClass()
        check(
            """     * @response 404 {<error descr="Template 'MissingTemplate' is not declared in getOpenApiTemplates() — the generated spec will carry a ${'$'}ref pointing at nothing.">MissingTemplate</error>}"""
        )
    }

    fun `test a security scheme nobody declared is an error`() {
        enableLaravelApi()
        addApiClass()
        check(
            """     * @security <error descr="Security scheme 'AdminSession' is not declared in getOpenApiSecurityDefinitions() — the spec will reference a scheme it never defines.">AdminSession</error>"""
        )
    }

    fun `test a declared security scheme is left alone`() {
        enableLaravelApi()
        addApiClass()
        check("""     * @security BearerAuth""")
    }

    fun `test security is not policed while the project declares no schemes`() {
        enableLaravelApi()
        // No Api class, so no definitions anywhere: the feature is evidently
        // not in use, and painting every @security red would be a wall of noise
        // rather than a finding.
        check("""     * @security Whatever""")
    }

    fun `test nothing fires without the package`() {
        // No stub: the same markup that errors above must pass unremarked here.
        check("""     * @input string name Description""")
    }
}
