package dev.dskripchenko.laravelapi.markup

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.PhpIndex
import dev.dskripchenko.laravelapi.inspections.UnknownTemplateInspection
import dev.dskripchenko.laravelapi.navigation.TemplateLookup

/**
 * Versions inherit their templates: `V2 extends V1`, and V1 declares them.
 *
 * The quick fix used to give V2 a templates method of its own holding only the
 * new template — which hid everything V1 declares from V2's spec. And a version
 * that adds to its parent with `array_merge(parent::…(), [...])` declared
 * templates the plugin could not see.
 */
class InheritedTemplatesTest : BasePlatformTestCase() {

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

    private fun addVersion(name: String, parent: String, body: String) {
        myFixture.addFileToProject(
            "app/Api/$name.php",
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

    private val v1 = """
        public static function getOpenApiTemplates(): array
        {
            return ['Base' => []];
        }
    """.trimIndent()

    private fun textOf(name: String): String =
        PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("app/Api/$name.php"))!!.text

    private fun declareIn(className: String, template: String) {
        val apiClass = PhpIndex.getInstance(project).getClassesByFQN("\\App\\Api\\$className").first()
        WriteCommandAction.runWriteCommandAction(project) { CreateTemplateFix(template).declareIn(project, apiClass) }
    }

    fun `test it adds to what a version inherits instead of hiding it`() {
        addPackage()
        addVersion("V1", "BaseApi", v1)
        addVersion("V2", "V1", "    // inherits")

        declareIn("V2", "Fresh")

        val v2 = textOf("V2")
        assertTrue("the override merges the parent's", v2.contains("array_merge(parent::getOpenApiTemplates(), ["))
        assertTrue("with the template in it", v2.contains("'Fresh' => []"))
        assertFalse("the parent is left alone", textOf("V1").contains("Fresh"))
        assertTrue(TemplateLookup.allNames(project).containsAll(setOf("Base", "Fresh")))
    }

    fun `test it writes into the version's own literal of an array_merge`() {
        addPackage()
        addVersion("V1", "BaseApi", v1)
        addVersion(
            "V2", "V1",
            """
            public static function getOpenApiTemplates(): array
            {
                return array_merge(parent::getOpenApiTemplates(), ['Mine' => []]);
            }
            """.trimIndent()
        )

        declareIn("V2", "Fresh")

        val v2 = textOf("V2")
        assertTrue(v2.contains("'Fresh' => []"))
        assertTrue("still merging", v2.contains("array_merge(parent::getOpenApiTemplates(),"))
        assertEquals("one method", 1, Regex("function getOpenApiTemplates").findAll(v2).count())
    }

    fun `test templates added through array_merge are declared`() {
        addPackage()
        addVersion("V1", "BaseApi", v1)
        addVersion(
            "V2", "V1",
            """
            public static function getOpenApiTemplates(): array
            {
                return array_merge(parent::getOpenApiTemplates(), ['Mine' => ['id' => 'integer!']]);
            }
            """.trimIndent()
        )

        assertEquals(setOf("Base", "Mine", "Error", "Success"), TemplateLookup.allNames(project))

        myFixture.enableInspections(UnknownTemplateInspection::class.java)
        myFixture.configureByText(
            "UserController.php",
            """
            <?php
            class UserController
            {
                /**
                 * @response 200 {Mine}
                 */
                public function create() {}
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, true)
    }
}
