package dev.dskripchenko.laravelapi.lint

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dskripchenko.laravelapi.settings.LaravelApiSettings

/**
 * When running the command is worth offering, and what is said when it is not.
 *
 * The process itself is not exercised here — that needs a whole Laravel
 * application — but the refusals are, because "the action quietly does nothing"
 * is the failure this checks against.
 */
class ApiLintRunnerTest : BasePlatformTestCase() {

    fun `test a project with no artisan cannot be asked`() {
        assertFalse(ApiLintRunner.isAvailable(project))
    }

    fun `test the refusal says why, in words`() {
        val result = ApiLintRunner.run(project)

        assertTrue(result is ApiLintRunner.Result.Unavailable)
        val reason = (result as ApiLintRunner.Result.Unavailable).reason

        // Not "failed": the sentence has to tell a person what is missing.
        assertTrue("the reason is not explanatory: $reason", reason.contains("artisan"))
    }

    fun `test php is looked for beyond the launcher's environment`() {
        myFixture.addFileToProject("artisan", "#!/usr/bin/env php")

        val result = ApiLintRunner.run(project)

        // On macOS an IDE started from Finder has no shell PATH, and the first
        // version of this refused to run for a developer whose terminal runs
        // `php artisan` daily. Any answer is acceptable here except "no php" on
        // a machine that plainly has one.
        val phpExists = listOf("/opt/homebrew/bin/php", "/usr/local/bin/php", "/usr/bin/php")
            .any { java.io.File(it).canExecute() }

        if (phpExists && result is ApiLintRunner.Result.Unavailable) {
            assertFalse(
                "php exists on this machine and was not found: ${result.reason}",
                result.reason.contains("No `php`"),
            )
        }
    }

    /**
     * A wrong interpreter is not the same failure as no interpreter, and it is
     * the worse one: running `artisan` under a PHP the project does not support
     * fails somewhere deep inside the application, with a message about a
     * syntax error in a vendored file.
     */
    fun `test a configured interpreter is refused by name when it cannot be run`() {
        myFixture.addFileToProject("artisan", "#!/usr/bin/env php")
        LaravelApiSettings.of(project).phpExecutable = "/nowhere/php8.9"

        try {
            val result = ApiLintRunner.run(project)

            assertTrue(result is ApiLintRunner.Result.Unavailable)
            assertTrue(
                "the reason has to name the path that was configured",
                (result as ApiLintRunner.Result.Unavailable).reason.contains("/nowhere/php8.9"),
            )
        } finally {
            LaravelApiSettings.of(project).phpExecutable = ""
        }
    }

    fun `test availability is a file question, not an index one`() {
        // Asked while a menu is being built, when the index may not be ready —
        // the mistake that made the tool window disappear.
        myFixture.addFileToProject("artisan", "#!/usr/bin/env php")

        assertTrue(ApiLintRunner.isAvailable(project))
    }
}

/**
 * That the action exists where a person would look for it.
 *
 * Registration is what has broken twice in this plugin — an extension point
 * spelled wrong, a tool window asking the index too early — and both times the
 * feature was complete and unreachable.
 */
class RunApiLintActionTest : BasePlatformTestCase() {

    fun `test the action is registered under its id`() {
        val action = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("LaravelApi.RunLint")

        assertNotNull("the action is not registered", action)
        assertTrue(action is RunApiLintAction)
    }

    fun `test it sits in the Tools menu`() {
        val tools = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .getAction("ToolsMenu") as com.intellij.openapi.actionSystem.ActionGroup

        val ids = tools.getChildren(null)
            .mapNotNull { com.intellij.openapi.actionSystem.ActionManager.getInstance().getId(it) }

        assertTrue("not in the Tools menu: the action would exist and be unfindable", ids.contains("LaravelApi.RunLint"))
    }
}
