package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The inspections through `enableInspections` + `checkHighlighting` — the same
 * machinery the editor runs them with.
 *
 * Not by calling the classes directly: a rule registered under a name the
 * platform does not know is invisible to a direct call and dead in the IDE.
 * That mistake has already cost this plugin one release.
 */
class InspectionTest : BasePlatformTestCase() {

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
                    return ['UserResponse' => ['id' => 'integer!']];
                }

                public static function getOpenApiSecurityDefinitions(): array
                {
                    return ['BearerAuth' => ['type' => 'apiKey']];
                }
            }
            """.trimIndent()
        )
    }

    private fun check(inspection: Class<out LocalInspectionTool>, docblock: String) {
        myFixture.enableInspections(inspection)
        myFixture.configureByText(
            "UserController.php",
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
        myFixture.checkHighlighting(true, false, true)
    }

    fun `test markup the generator cannot read`() {
        enableLaravelApi()
        check(
            MalformedTagInspection::class.java,
            """     * <error descr="@input does not parse and will be ignored — expected: type ?${'$'}name Description">@input string name Description</error>"""
        )
    }

    fun `test unknown type is a warning naming the consequence`() {
        enableLaravelApi()
        check(
            UnknownTypeInspection::class.java,
            """     * @input <warning descr="Unknown type 'datetime' — the generator will call it 'string'. Known types: string, file, number, integer, boolean, array, object">datetime</warning> ${'$'}when When"""
        )
    }

    fun `test a template nobody declared`() {
        enableLaravelApi()
        addApiClass()
        check(
            UnknownTemplateInspection::class.java,
            """     * @response 404 {<error descr="Template 'MissingTemplate' is not declared in getOpenApiTemplates() — the generated spec will carry a ${'$'}ref pointing at nothing">MissingTemplate</error>}"""
        )
    }

    fun `test a declared template passes`() {
        enableLaravelApi()
        addApiClass()
        check(UnknownTemplateInspection::class.java, """     * @response 200 {UserResponse}""")
    }

    fun `test a scheme nobody declared`() {
        enableLaravelApi()
        addApiClass()
        check(
            UnknownSecuritySchemeInspection::class.java,
            """     * @security <error descr="Security scheme 'AdminSession' is not declared in getOpenApiSecurityDefinitions() — the spec will reference a scheme it never defines">AdminSession</error>"""
        )
    }

    fun `test security is not policed while no scheme exists`() {
        enableLaravelApi()
        check(UnknownSecuritySchemeInspection::class.java, """     * @security Whatever""")
    }

    fun `test a field declared twice`() {
        enableLaravelApi()
        check(
            MarkupConsistencyInspection::class.java,
            """     * @input string ${'$'}title Title
     * <warning descr="@input ${'$'}title is declared twice; the last one wins">@input string ${'$'}title Again</warning>"""
        )
    }

    fun `test nesting whose parent is never declared`() {
        enableLaravelApi()
        check(
            MarkupConsistencyInspection::class.java,
            """     * <warning descr="@input ${'$'}address.city is nested under ${'$'}address, which is never declared">@input string ${'$'}address.city City</warning>"""
        )
    }

    fun `test a parent declared as the wrong container`() {
        enableLaravelApi()
        check(
            MarkupConsistencyInspection::class.java,
            """     * @input string ${'$'}tags Tags
     * <warning descr="@input ${'$'}tags[].id needs ${'$'}tags to be `array`, and it is declared `string`">@input integer ${'$'}tags[].id Tag id</warning>"""
        )
    }

    fun `test nesting declared after its parent passes`() {
        enableLaravelApi()
        check(
            MarkupConsistencyInspection::class.java,
            """     * @input string ${'$'}address.city City
     * @input object ${'$'}address Address"""
        )
    }

    fun `test two answers for one status code`() {
        enableLaravelApi()
        check(
            MarkupConsistencyInspection::class.java,
            """     * @response 200 Fine
     * <warning descr="@response 200 is declared twice; the last one wins and the other body is lost from the spec">@response 200 Also fine</warning>"""
        )
    }

    fun `test nothing fires without the package`() {
        check(MalformedTagInspection::class.java, """     * @input string name Description""")
    }
}
