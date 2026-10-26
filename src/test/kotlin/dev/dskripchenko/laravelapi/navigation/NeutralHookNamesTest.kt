package dev.dskripchenko.laravelapi.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.inspections.UnknownSecuritySchemeInspection
import dev.dskripchenko.laravelapi.inspections.UnknownTemplateInspection
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * laravel-api 6.1 names the hooks `getSchemaTemplates()` and
 * `getSecuritySchemes()` too. The package reads the neutral name, and its own
 * implementation of it answers with the old one — so what counts is the name an
 * application overrides.
 */
class NeutralHookNamesTest : BasePlatformTestCase() {

    /** BaseApi as 6.0 ships it: only the old names. */
    private fun addPackage60() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi
            {
                public static function getOpenApiTemplates(): array { return []; }
                public static function getOpenApiSecurityDefinitions(): array { return []; }
            }
            """.trimIndent()
        )
    }

    /** BaseApi as 6.1 ships it: the neutral names answer with the old ones. */
    private fun addPackage61() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi
            {
                public static function getOpenApiTemplates(): array { return []; }
                public static function getOpenApiSecurityDefinitions(): array { return []; }
                public static function getSchemaTemplates(): array { return static::getOpenApiTemplates(); }
                public static function getSecuritySchemes(): array { return static::getOpenApiSecurityDefinitions(); }
            }
            """.trimIndent()
        )
    }

    private fun addApiClass(body: String, path: String = "app/Api/V1.php", name: String = "V1", parent: String = "BaseApi") {
        myFixture.addFileToProject(
            path,
            """
            <?php
            namespace App\Api;
            use Dskripchenko\LaravelApi\Components\BaseApi;
            class $name extends $parent
            {
            $body
            }
            """.trimIndent()
        )
    }

    private fun check(docblock: String) {
        myFixture.enableInspections(UnknownTemplateInspection::class.java, UnknownSecuritySchemeInspection::class.java)
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

    private val neutralDeclarations = """
        public static function getSchemaTemplates(): array
        {
            return ['UserResponse' => ['id' => 'integer!']];
        }

        public static function getSecuritySchemes(): array
        {
            return ['BearerAuth' => ['type' => 'apiKey']];
        }
    """.trimIndent()

    fun `test templates and schemes declared under the neutral names are found`() {
        addPackage61()
        addApiClass(neutralDeclarations)

        check("     * @response 200 {UserResponse}\n     * @security BearerAuth")
    }

    fun `test the old names still work on 6_1, where the package's own neutral hook is not an override`() {
        addPackage61()
        addApiClass(
            """
            public static function getOpenApiTemplates(): array
            {
                return ['UserResponse' => []];
            }
            """.trimIndent()
        )

        check("     * @response 200 {UserResponse}")
    }

    fun `test the neutral name wins when a class overrides both, as it does in the package`() {
        addPackage61()
        addApiClass(
            """
            public static function getSchemaTemplates(): array
            {
                return ['UserResponse' => []];
            }

            public static function getOpenApiTemplates(): array
            {
                return ['Legacy' => []];
            }
            """.trimIndent()
        )

        check(
            """     * @response 200 {<error descr="Template 'Legacy' is not declared in getSchemaTemplates() — the generated spec will carry a ${'$'}ref pointing at nothing">Legacy</error>}"""
        )
    }

    fun `test a version inherits the neutral declarations of its parent`() {
        addPackage61()
        addApiClass(neutralDeclarations)
        addApiClass("    // inherits", path = "app/Api/V2.php", name = "V2", parent = "V1")

        assertEquals(setOf("UserResponse", "Error", "Success"), TemplateLookup.allNames(project))
        assertEquals(setOf("BearerAuth"), SecurityLookup.allNames(project))
    }

    fun `test the messages name the hook the project uses`() {
        addPackage61()
        addApiClass(neutralDeclarations)

        check(
            """     * @response 404 {<error descr="Template 'Missing' is not declared in getSchemaTemplates() — the generated spec will carry a ${'$'}ref pointing at nothing">Missing</error>}
     * @security <error descr="Security scheme 'AdminSession' is not declared in getSecuritySchemes() — the spec will reference a scheme it never defines">AdminSession</error>"""
        )
    }

    fun `test the preferred name follows the project, then the package`() {
        addPackage60()
        assertEquals("getOpenApiTemplates", ApiHooks.TEMPLATES.preferredName(project))
    }

    fun `test a project on 6_1 that declares nothing yet is offered the neutral name`() {
        addPackage61()
        addApiClass("    // nothing yet")

        assertEquals("getSchemaTemplates", ApiHooks.TEMPLATES.preferredName(project))
        assertEquals("getSecuritySchemes", ApiHooks.SECURITY.preferredName(project))
    }

    fun `test a project on 6_1 already using the old name keeps it`() {
        addPackage61()
        addApiClass(
            """
            public static function getOpenApiTemplates(): array
            {
                return [];
            }
            """.trimIndent()
        )

        assertEquals("getOpenApiTemplates", ApiHooks.TEMPLATES.preferredName(project))
    }

    fun `test a key of getSchemaTemplates is a template declaration`() {
        addPackage61()
        myFixture.configureByText(
            "V1.php",
            """
            <?php
            namespace App\Api;
            use Dskripchenko\LaravelApi\Components\BaseApi;
            class V1 extends BaseApi
            {
                public static function getSchemaTemplates(): array
                {
                    return ['User<caret>Response' => ['id' => 'integer!']];
                }
            }
            """.trimIndent()
        )

        val key = myFixture.file.findElementAt(myFixture.caretOffset)
            ?.let { com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, StringLiteralExpression::class.java, false) }

        assertNotNull(key)
        assertTrue(TemplateUsages.isTemplateDeclaration(key!!))
    }
}
