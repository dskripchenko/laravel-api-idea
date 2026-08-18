package dev.dskripchenko.laravelapi.lint

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * "Run api:lint" from the menu.
 *
 * Opens the window and starts the run, so the findings land where they can be
 * clicked rather than in a console.
 */
class RunApiLintAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project

        // Enabled only where there is an application to ask. Presence is a file
        // check, so the answer does not depend on the index being ready.
        event.presentation.isEnabledAndVisible = project != null && ApiLintRunner.isAvailable(project)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val window = ToolWindowManager.getInstance(project).getToolWindow("Laravel API") ?: return

        window.activate {
            val content = window.contentManager.contents.firstOrNull { it.displayName == "Lint" } ?: return@activate
            window.contentManager.setSelectedContent(content)
            (content.component as? LintPanel)?.start()
        }
    }
}
