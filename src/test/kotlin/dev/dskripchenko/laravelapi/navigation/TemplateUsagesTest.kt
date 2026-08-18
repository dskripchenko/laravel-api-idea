package dev.dskripchenko.laravelapi.navigation

import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTargetUtil

/**
 * Find Usages on a template declaration, through the platform's own action.
 *
 * The question it answers is "can this schema go" — today a text search, which
 * also finds the name in prose and in unrelated variables.
 */
class TemplateUsagesTest : BasePlatformTestCase() {

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

                /**
                 * @output @UserResponse ${'$'}user The same template, another form
                 */
                public function card() {}

                /**
                 * Prose mentioning UserResponse, and a variable named after it.
                 *
                 * @input string ${'$'}userResponse Not a reference
                 * @response 200 {OrderResponse}
                 */
                public function unrelated() {}
            }
            """.trimIndent()
        )
    }

    private fun configureApiClass() {
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
                        '<caret>UserResponse' => ['id' => 'integer!'],
                        'OrderResponse' => ['total' => 'number'],
                    ];
                }
            }
            """.trimIndent()
        )
    }

    /**
     * Through the registered factory, not by constructing ours.
     *
     * `myFixture.testFindUsages` cannot be used here: it resolves the caret to
     * a *reference target*, and a template name is deliberately not one — that
     * is the whole reason this feature is a handler. Going through the
     * extension point still proves the part that has bitten this plugin
     * before: that the thing is registered at all.
     */
    private fun usagesAtCaret(): List<String> {
        val element: PsiElement = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent

        val factory = FindUsagesHandlerFactory.EP_NAME.getExtensions(project)
            .firstOrNull { it is TemplateFindUsagesHandlerFactory }
        assertNotNull("the factory is not registered with the platform", factory)
        assertTrue("the declaration should be searchable", factory!!.canFindUsages(element))

        val handler = factory.createFindUsagesHandler(element, false)!!
        val found = mutableListOf<String>()
        handler.processElementUsages(element, { info: UsageInfo -> found += info.element?.text.orEmpty(); true }, handler.findUsagesOptions)

        return found
    }

    /**
     * The step the user performs first, and the one that was missing.
     *
     * Before any handler is consulted, the platform asks what the caret is on.
     * A key in an array literal is neither a named element nor a reference
     * target, so the answer was "nothing" and ⌥F7 replied "Cannot search for
     * usages from this location" — with a working handler sitting behind it.
     */
    fun `test the caret on a declaration is something usages can be found for`() {
        addProject()
        configureApiClass()

        val targets = UsageTargetUtil.findUsageTargets(myFixture.editor, myFixture.file)

        assertNotNull("the platform sees nothing to search for", targets)
        assertTrue("the declaration is not offered as a target", targets!!.isNotEmpty())
    }

    fun `test a field of a template is not offered as a target`() {
        addProject()
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
                    return ['UserResponse' => ['<caret>id' => 'integer!']];
                }
            }
            """.trimIndent()
        )

        val provider = TemplateUsageTargetProvider()

        assertNull(provider.getTargets(myFixture.editor, myFixture.file))
    }

    fun `test the declaration finds the docblocks that name it`() {
        addProject()
        configureApiClass()

        val usages = usagesAtCaret()

        // Two real references, in both forms the markup allows.
        assertEquals(2, usages.size)
        assertTrue(usages.any { it.contains("@response 200 {UserResponse}") })
        assertTrue(usages.any { it.contains("@output @UserResponse") })
    }

    fun `test prose and same-named variables are not usages`() {
        addProject()
        configureApiClass()

        val usages = usagesAtCaret()

        // The word occurs in a sentence and in `$userResponse`; neither refers
        // to the template, and a text search would offer both.
        assertTrue(usages.none { it.contains("Not a reference") })
        assertTrue(usages.none { it.contains("Prose mentioning") })
    }

    fun `test a field of a template is not a declaration`() {
        addProject()
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
                    return ['UserResponse' => ['<caret>id' => 'integer!']];
                }
            }
            """.trimIndent()
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent

        // `'id'` is a field, not a template: asking who uses it makes no sense,
        // and offering the search would be a lie about what it searches.
        assertFalse(TemplateUsages.isTemplateDeclaration(element))
    }
}
