package dev.dskripchenko.laravelapi.routes

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.dskripchenko.laravelapi.export.EndpointExporter
import dev.dskripchenko.laravelapi.export.ExportFormat
import dev.dskripchenko.laravelapi.lint.Artisan

/**
 * Everything that can be done with one endpoint, in one list.
 *
 * There used to be a link and, somewhere else entirely, an export action in the
 * editor's context menu. Two affordances for one subject, and the second one
 * discoverable only by someone who already knew it existed. This is the single
 * place: read the documentation of this endpoint, or take it away as a request
 * in whatever a client tool reads.
 *
 * What the list contains depends on what is actually possible, and the absence
 * of an item is the answer to "why can I not": a version assembled at runtime
 * has no documentation link, a project with no `artisan` has no export.
 */
object EndpointMenu {

    /** One line of the menu and what it does. */
    data class Item(val label: String, val run: () -> Unit)

    /**
     * The endpoint, named the way `api:export` takes it.
     *
     * Null [version] means no module names the Api class literally — the
     * endpoint exists and cannot be addressed by name, which rules out both the
     * documentation and the export.
     */
    data class Target(val entry: RouteMapLookup.ActionEntry, val version: String?)

    fun itemsFor(project: Project, targets: List<Target>, links: List<EndpointDocs.Link>): List<Item> {
        val items = mutableListOf<Item>()

        for (link in links) {
            items += Item("Documentation — ${link.version}.${link.httpMethod.uppercase()}") {
                BrowserUtil.browse(link.url)
            }
        }

        // Exporting needs a name and an application to ask; the documentation
        // needs an address. Neither implies the other, so each is offered on its
        // own terms.
        val named = targets.firstOrNull { it.version != null } ?: return items
        if (!Artisan.isAvailable(project)) return items

        val endpoint = EndpointExporter.endpointName(
            named.version!!,
            named.entry.controllerKey,
            named.entry.actionKey,
        )
        val httpMethod = named.entry.httpMethods.singleOrNull()

        for (format in ExportFormat.entries) {
            items += Item("Export as ${format.label}") {
                export(project, endpoint, format, httpMethod)
            }
        }

        return items
    }

    /**
     * Shows the list, or acts directly when there is only one thing to do.
     *
     * A popup with a single line in it asks a question that has one answer.
     */
    fun show(items: List<Item>, title: String, position: (JBPopup) -> Unit) {
        if (items.isEmpty()) return

        if (items.size == 1) {
            items.first().run()

            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items.map { it.label })
            .setTitle(title)
            .setItemChosenCallback { chosen ->
                items.firstOrNull { it.label == chosen }?.run?.invoke()
            }
            .createPopup()

        position(popup)
    }

    private fun export(project: Project, endpoint: String, format: ExportFormat, httpMethod: String?) {
        object : Task.Backgroundable(project, "Exporting $endpoint", true) {
            private var result: EndpointExporter.Result? = null

            override fun run(indicator: ProgressIndicator) {
                // Booting an application takes as long as it takes; on the event
                // thread it would freeze the editor for the duration.
                result = EndpointExporter.export(project, endpoint, format, httpMethod)
            }

            override fun onSuccess() {
                when (val outcome = result) {
                    is EndpointExporter.Result.Exported ->
                        EndpointExporter.openInScratch(project, endpoint, format, outcome.content)

                    is EndpointExporter.Result.Failed ->
                        Messages.showErrorDialog(project, outcome.reason, "api:export Failed")

                    null -> Unit
                }
            }
        }.queue()
    }
}
