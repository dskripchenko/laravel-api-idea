package dev.dskripchenko.laravelapi.lint

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment
import dev.dskripchenko.laravelapi.routes.RouteMapLookup
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * The findings of `api:lint`, in the editor's own window.
 *
 * The command already prints them, and a person can read a terminal. What the
 * terminal cannot do is take you to the docblock: its address is
 * `version · controller.action`, which the plugin already knows how to turn
 * into a method.
 */
class LintPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private data class Row(val finding: LintFinding) {
        override fun toString(): String {
            val mark = if (finding.isError) "error  " else "warning"

            return "$mark  ${finding.where}  —  ${finding.message}"
        }
    }

    private val model = DefaultListModel<Row>()
    private val list = JBList(model)
    private val status = JBLabel("Press Run to ask the application")
    private val run = JButton("Run api:lint")

    override fun dispose() = Unit

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) open()
            }
        })

        run.addActionListener { start() }

        border = JBUI.Borders.empty(4)
        add(run, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    fun start() {
        if (!ApiLintRunner.isAvailable(project)) {
            status.text = "No artisan here — api:lint is a command of the application"

            return
        }

        model.clear()
        status.text = "Running…"
        run.isEnabled = false

        // A whole Laravel application boots for this, which is seconds rather
        // than milliseconds: on the event thread it would freeze the IDE.
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running api:lint", true) {
            override fun run(indicator: ProgressIndicator) {
                val result = ApiLintRunner.run(project)

                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(
                    { show(result) },
                    ModalityState.defaultModalityState(),
                )
            }
        })
    }

    private fun show(result: ApiLintRunner.Result) {
        run.isEnabled = true

        when (result) {
            is ApiLintRunner.Result.Unavailable -> status.text = result.reason

            is ApiLintRunner.Result.Failed -> {
                // The application's own words, not a paraphrase: when it
                // refuses to boot, its message is the useful one.
                status.text = result.reason
                result.output.lines().take(20).forEach { model.addElement(Row(LintFinding("error", "", "", it, null))) }
            }

            is ApiLintRunner.Result.Report -> {
                result.findings.forEach { model.addElement(Row(it)) }

                val errors = result.findings.count { it.isError }
                val warnings = result.findings.size - errors

                status.text = if (result.findings.isEmpty()) {
                    "No issues found"
                } else {
                    "$errors error(s), $warnings warning(s) — double-click to open"
                }
            }
        }
    }

    /**
     * Opens the docblock a finding is about.
     *
     * The address names an endpoint, not a file, so the route map answers where
     * it lives. A finding about a version as a whole has nowhere to go, and
     * nothing happens rather than a jump to something approximate.
     */
    private fun open() {
        val endpoint = list.selectedValue?.finding?.endpoint ?: return

        val target = ReadAction.compute<PhpDocComment?, RuntimeException> {
            val entry = RouteMapLookup.allActions(project)
                .firstOrNull { "${it.controllerKey}.${it.actionKey}" == endpoint }
                ?: return@compute null

            RouteMapLookup.targetMethod(project, entry)?.docComment
        }

        target?.navigate(true)
    }
}
