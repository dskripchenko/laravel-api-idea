package dev.dskripchenko.laravelapi.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Whether a finding can be suppressed.
 *
 * The whole argument for moving off annotators was that the platform then owns
 * severity, the on/off switch and suppression. The first two are visible in the
 * settings tree; the third is a claim until something checks it — and the
 * problems here are reported on docblock elements, which is not where
 * `@noinspection` usually goes.
 */
class SuppressionTest : BasePlatformTestCase() {

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

    private fun intentionsAtCaret(): List<String> {
        myFixture.enableInspections(UnknownTypeInspection::class.java)
        myFixture.configureByText(
            "UserController.php",
            """
            <?php
            class UserController
            {
                /**
                 * @input date<caret>time ${'$'}when When
                 */
                public function create() {}
            }
            """.trimIndent()
        )

        return myFixture.availableIntentions.map { it.text }
    }

    fun `test a finding can be suppressed from the editor`() {
        enableLaravelApi()

        val offered = intentionsAtCaret()

        // Named exactly, because the point is what a person is offered, not
        // that the word appears somewhere: the docblock is the narrowest scope
        // PHP suppresses at, and it is the one that matters here.
        assertTrue(
            "no way to suppress this finding; offered: $offered",
            offered.contains("Suppress for PhpDoc comment"),
        )
        assertTrue(offered.contains("Suppress for file"))

        // And the settings route, which is the other half of the move off
        // annotators.
        assertTrue(offered.contains("Disable inspection"))
        assertTrue(offered.any { it.startsWith("Inspection 'Laravel API") })
    }
}
