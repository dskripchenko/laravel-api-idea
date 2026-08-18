package dev.dskripchenko.laravelapi.lint

import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
