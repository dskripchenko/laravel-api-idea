package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import dev.dskripchenko.laravelapi.LaravelApiProject
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * Every endpoint of the project, in one searchable list.
 *
 * The route map is spread across the Api classes of every version and panel, and
 * an action key is a string: "where is `print-form.batch` handled" is a question
 * currently answered by grep. One real application has dozens of them across two
 * panels.
 *
 * The list is rebuilt on demand rather than kept in sync — it is opened to
 * answer a question, and a stale answer is worse than a second's wait.
 */
class EndpointsToolWindowFactory : ToolWindowFactory, DumbAware {

    /**
     * Always registered, and this is a correction rather than a preference.
     *
     * It used to answer `LaravelApiProject.isEnabled(project)`, which asks the
     * PHP index. The platform decides availability while a project is opening —
     * before indexing has finished — so the index answered "no package here",
     * the window was never registered, and no amount of looking would find it.
     * A feature that hides itself is worse than a tab in a project that does not
     * need one, so the panel explains itself instead.
     */
    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = EndpointsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }
}

private class EndpointsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private data class Row(val label: String, val entry: RouteMapLookup.ActionEntry) {
        override fun toString(): String = label
    }

    private val model = DefaultListModel<Row>()
    private val list = JBList(model)
    private val search = SearchTextField()
    private var all: List<Row> = emptyList()

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                // Double click, like every other list in the IDE that navigates.
                if (event.clickCount == 2) open()
            }
        })

        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = refilter()
        })

        border = JBUI.Borders.empty(4)

        if (LaravelApiProject.isEnabled(project)) {
            add(search, BorderLayout.NORTH)
            add(JBScrollPane(list), BorderLayout.CENTER)
            reload()
        } else {
            // Said plainly rather than left as an empty list: "no endpoints" and
            // "this project has nothing to do with the package" look identical
            // otherwise.
            add(
                JBLabel(
                    "<html><body style='padding:8px'>This project does not use " +
                        "<code>dskripchenko/laravel-api</code>.<br><br>" +
                        "If it has just been added, the list appears once indexing finishes." +
                        "</body></html>"
                ),
                BorderLayout.CENTER,
            )
        }
    }

    private fun reload() {
        all = RouteMapLookup.allActions(project)
            .map { Row("${it.controllerKey}.${it.actionKey}  →  ${it.methodName}()", it) }
            .sortedBy { it.label }

        refilter()
    }

    private fun refilter() {
        val query = search.text.trim().lowercase()
        model.clear()

        all.filter { query.isEmpty() || it.label.lowercase().contains(query) }
            .forEach(model::addElement)
    }

    /**
     * Opens the method the selected action routes.
     *
     * An action whose method is missing has nowhere to go — the same 404 the
     * inspection reports — so nothing happens rather than an error popup.
     */
    private fun open() {
        val row = list.selectedValue ?: return
        RouteMapLookup.targetMethod(project, row.entry)?.navigate(true)
    }
}
