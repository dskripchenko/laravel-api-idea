package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.php.lang.psi.elements.Method
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.lint.LintPanel
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
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
        val content = ContentFactory.getInstance().createContent(panel, "Endpoints", false)
        // So a background read started by the panel is cancelled when the
        // window goes away, rather than finishing into a component nobody is
        // looking at any more.
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)

        // The linter lives beside the endpoints rather than in a window of its
        // own: both answer questions about the same map, and two tool windows
        // for one package is one too many.
        val lint = LintPanel(project)
        val lintContent = ContentFactory.getInstance().createContent(lint, "Lint", false)
        lintContent.setDisposer(lint)
        toolWindow.contentManager.addContent(lintContent)
    }
}

private class EndpointsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    override fun dispose() = Unit

    /** [version] is null when no module names the Api class literally. */
    private data class Row(
        val label: String,
        val entry: RouteMapLookup.ActionEntry,
        val version: String?,
    ) {
        override fun toString(): String = label
    }

    private val model = DefaultListModel<Row>()
    private val list = JBList(model)
    private val search = SearchTextField()
    private val status = JBLabel()
    private var all: List<Row> = emptyList()

    init {
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                // Double click, like every other list in the IDE that navigates.
                if (event.clickCount == 2) open()
            }

            // Both, because which one carries the popup is a platform question:
            // on macOS it is the press, elsewhere the release.
            override fun mousePressed(event: MouseEvent) = maybePopup(event)

            override fun mouseReleased(event: MouseEvent) = maybePopup(event)
        })

        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = refilter()
        })

        border = JBUI.Borders.empty(4)
        add(search, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        // The window is registered before the project finishes opening, so the
        // first read of the index happens while it is still empty — which is
        // how the list came up blank and stayed that way. Read it again once
        // the IDE is in smart mode, and let the window's own activation refresh
        // it afterwards.
        reload()

        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    if (toolWindow.id == "Laravel API") reload()
                }
            },
        )
    }

    /**
     * Rebuilds the list off the event thread.
     *
     * Reading PSI or the index requires a read action, and doing it on the
     * event thread is what threw
     * "Read access is allowed from inside read-action only" the first time this
     * window was used in anger. `nonBlocking` also keeps the interface
     * responsive: on a real project the map spans every Api class of every
     * panel, and that is not work to do between two mouse events.
     */
    private fun reload() {
        ReadAction.nonBlocking<Pair<List<Row>, String>> {
            if (!LaravelApiProject.isEnabled(project)) {
                // Said plainly: an empty list and "this project has nothing to
                // do with the package" look identical otherwise.
                return@nonBlocking emptyList<Row>() to "This project does not use dskripchenko/laravel-api"
            }

            // The version comes from a different place than the actions —
            // the module, not the Api class — so it is read once per rebuild
            // rather than per row.
            val versions = ApiVersionLookup.versionsByApi(project)

            val rows = RouteMapLookup.allActions(project)
                .flatMap { entry ->
                    val names = ApiVersionLookup.versionsOf(entry, versions)

                    if (names.isEmpty()) {
                        listOf(Row(EndpointLabel.of(entry, null), entry, null))
                    } else {
                        names.map { Row(EndpointLabel.of(entry, it), entry, it) }
                    }
                }
                .sortedBy { it.label }

            val note = if (rows.isEmpty()) {
                "No endpoints found — getMethods() may be built dynamically"
            } else {
                "${rows.size} endpoint(s)"
            }

            rows to note
        }
            // The index answers nothing useful while it is still being built,
            // and a list that came up empty for that reason used to stay empty.
            .inSmartMode(project)
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { (rows, note) ->
                all = rows
                status.text = note
                refilter()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun refilter() {
        val query = search.text.trim().lowercase()
        model.clear()

        all.filter { query.isEmpty() || it.label.lowercase().contains(query) }
            .forEach(model::addElement)
    }

    /**
     * The right-click menu — one item, and it exists because the gutter's icon
     * cannot explain itself.
     *
     * A link that cannot be built shows no icon in the editor, which is right
     * there and wrong here: this list is where one comes to ask about an
     * endpoint, and "no menu item" would answer nothing. Here the refusal is
     * spelled out.
     */
    private fun maybePopup(event: MouseEvent) {
        if (!event.isPopupTrigger) return

        val index = list.locationToIndex(event.point).takeIf { it >= 0 } ?: return
        if (!list.getCellBounds(index, index).contains(event.point)) return
        list.selectedIndex = index

        val menu = JPopupMenu()
        menu.add(JMenuItem("API…").apply { addActionListener { openMenu() } })
        menu.show(event.component, event.x, event.y)
    }

    /**
     * The same list the editor shows above the method — documentation, and the
     * endpoint as a request in each format.
     *
     * Here, unlike in the editor, an empty list is answered in words. The
     * editor can afford to show nothing: a line that is not there asks no
     * question. This window is where one comes to ask about an endpoint, and
     * silence would answer nothing.
     */
    private fun openMenu() {
        val row = list.selectedValue ?: return

        ReadAction.nonBlocking<Pair<List<EndpointMenu.Item>, String?>> {
            val context = EndpointDocs.context(project)
            val links = context?.let { EndpointDocs.linksOf(row.entry, it) } ?: emptyList()
            val targets = listOf(EndpointMenu.Target(row.entry, row.version))
            val items = EndpointMenu.itemsFor(project, targets, links)

            items to if (items.isEmpty()) refusal(row) else null
        }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { (items, refusal) ->
                if (refusal != null) {
                    Messages.showInfoMessage(project, refusal, "Nothing to Do With This Endpoint")
                } else {
                    EndpointMenu.show(items, "API") { popup -> popup.showInFocusCenter() }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Why the list came out empty — each cause is a different fix. */
    private fun refusal(row: Row): String = when (val docs = EndpointDocs.of(project, row.entry)) {
        is EndpointDocs.Result.Unavailable -> docs.reason

        is EndpointDocs.Result.Links ->
            "No `artisan` in this project, so there is nothing to export with."
    }

    /**
     * Opens the method the selected action routes.
     *
     * An action whose method is missing has nowhere to go — the same 404 the
     * inspection reports — so nothing happens rather than an error popup.
     */
    private fun open() {
        val row = list.selectedValue ?: return

        // Resolving the method touches the index, so it happens inside a read
        // action; navigation is a UI act and stays on this thread.
        ReadAction.nonBlocking<Method?> { RouteMapLookup.targetMethod(project, row.entry) }
            .expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { it?.navigate(true) }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
