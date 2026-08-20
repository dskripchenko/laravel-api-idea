package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.php.lang.PhpLanguage
import dev.dskripchenko.laravelapi.settings.LaravelApiSettings

/**
 * The icon appears where the question is asked, and only when it will work.
 *
 * An icon that opens nothing is worse than no icon: it is a promise, and the
 * reader who follows it lands on a page that scrolls nowhere and concludes the
 * documentation is broken.
 */
class DocLinkLineMarkerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        LaravelApiSettings.of(project).docsBaseUrl = "https://example.test"

        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseModule.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            class BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [];
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "app/Api/Controllers/TemplateController.php",
            """
            <?php
            namespace App\Api\Controllers;

            class TemplateController
            {
                public function contract() {}
            }
            """.trimIndent()
        )
    }

    override fun tearDown() {
        try {
            LaravelApiSettings.of(project).docsBaseUrl = ""
        } finally {
            super.tearDown()
        }
    }

    private fun addModule(versions: String) {
        myFixture.addFileToProject(
            "app/Api/Module.php",
            """
            <?php
            namespace App\Api;

            use Dskripchenko\LaravelApi\Components\BaseModule;

            class Module extends BaseModule
            {
                public function getApiVersionList(): array
                {
                    return [$versions];
                }
            }
            """.trimIndent()
        )
    }

    private fun apiSource() = """
        <?php
        namespace App\Api;

        use Dskripchenko\LaravelApi\Components\BaseApi;

        class V1 extends BaseApi
        {
            public static function getMethods(): array
            {
                return [
                    'controllers' => [
                        'template' => [
                            'controller' => \App\Api\Controllers\TemplateController::class,
                            'actions' => [
                                'contract' => ['action' => 'contract', 'method' => ['get']],
                            ],
                        ],
                    ],
                ];
            }
        }
    """.trimIndent()

    /** Every marker the provider produces for the file under the caret. */
    private fun markers(): List<LineMarkerInfo<*>> {
        val provider = LineMarkerProviders.getInstance().allForLanguage(PhpLanguage.INSTANCE)
            .filterIsInstance<DocLinkLineMarkerProvider>()
            .single()

        val leaves = com.intellij.psi.util.PsiTreeUtil
            .findChildrenOfType(myFixture.file, com.intellij.psi.PsiElement::class.java)
            .filter { it.firstChild == null }

        val result = mutableListOf<LineMarkerInfo<*>>()
        provider.collectSlowLineMarkers(leaves, result)

        return result
    }

    fun `test the route map entry carries a way into the documentation`() {
        addModule("'integration' => V1::class")
        myFixture.configureByText("V1.php", apiSource())

        assertTrue("no marker on the action key", markers().isNotEmpty())
    }

    fun `test the provider is registered, or the icon exists and never appears`() {
        val registered = LineMarkerProviders.getInstance().allForLanguage(PhpLanguage.INSTANCE)
            .any { it is DocLinkLineMarkerProvider }

        assertTrue("the provider is not registered for PHP", registered)
    }

    fun `test nothing is drawn when no module names the version`() {
        // The list is built at runtime — nothing static can name the version,
        // and an icon here would open the page at an anchor that is not on it.
        myFixture.configureByText("V1.php", apiSource())

        assertTrue(markers().isEmpty())
    }

    fun `test nothing is drawn when there is no address to open`() {
        LaravelApiSettings.of(project).docsBaseUrl = ""
        addModule("'integration' => V1::class")
        myFixture.configureByText("V1.php", apiSource())

        assertTrue(markers().isEmpty())
    }
}
