package dev.dskripchenko.laravelapi.export

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.concurrency.AppExecutorUtil
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.lint.Artisan
import dev.dskripchenko.laravelapi.routes.ApiVersionLookup
import dev.dskripchenko.laravelapi.routes.EndpointAtCaret
import dev.dskripchenko.laravelapi.routes.RouteMapLookup

/**
 * Export the endpoint under the caret as a request a client tool can open.
 *
 * The whole point is the "under the caret" part. `api:export` has been able to
 * produce a collection for a while, and producing one for a hundred endpoints
 * to try a single one is the reason nobody did it. From here the question is
 * asked where it comes up — in the controller method, or in the route map entry
 * that points at it — and the answer is one request.
 */
class ExportEndpointAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.PSI_FILE)
        val editor = event.getData(CommonDataKeys.EDITOR)

        // Every check here is cheap on purpose: this runs whenever a menu is
        // built, and the expensive question — is there really an endpoint here —
        // is asked once, when the action is actually invoked.
        event.presentation.isEnabledAndVisible = project != null &&
            file != null &&
            editor != null &&
            LaravelApiProject.isEnabled(project) &&
            Artisan.isAvailable(project) &&
            EndpointAtCaret.looksLikeOne(file, editor.caretModel.offset)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val offset = editor.caretModel.offset

        // Resolving walks the Api classes of the project, which is a read action
        // and not work for the event thread — the mistake this plugin's tool
        // window made the first time it was used in anger.
        ReadAction.nonBlocking<List<Named>> {
            val versions = ApiVersionLookup.versionsByApi(project)

            EndpointAtCaret.resolve(file, offset).flatMap { entry ->
                ApiVersionLookup.versionsOf(entry, versions).map { Named(entry, it) }
            }
        }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { named ->
                if (named.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "No endpoint here that can be named: an action needs a version, and a module that " +
                            "builds its version list at runtime cannot be read from the source.",
                        "Nothing to Export",
                    )
                } else {
                    chooseEndpoint(project, named) { chosen -> chooseFormat(project, chosen) }
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** An entry and the version it answers under — together, a route's name. */
    private data class Named(val entry: RouteMapLookup.ActionEntry, val version: String) {
        val endpoint: String = EndpointExporter.endpointName(version, entry.controllerKey, entry.actionKey)

        val method: String? = entry.httpMethods.singleOrNull()

        val label: String = if (method != null) "${method.uppercase()}  $endpoint" else endpoint
    }

    private fun chooseEndpoint(project: Project, options: List<Named>, then: (Named) -> Unit) {
        if (options.size == 1) {
            then(options.first())

            return
        }

        // One method routed by two versions is two endpoints, and exporting the
        // wrong one is a request that fails for reasons nobody can see.
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(options.map { it.label })
            .setTitle("Which Endpoint")
            .setItemChosenCallback { chosen -> options.firstOrNull { it.label == chosen }?.let(then) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun chooseFormat(project: Project, endpoint: Named) {
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ExportFormat.entries.map { it.label })
            .setTitle("Export ${endpoint.endpoint} As")
            .setItemChosenCallback { chosen ->
                ExportFormat.byLabel(chosen)?.let { export(project, endpoint, it) }
            }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun export(project: Project, endpoint: Named, format: ExportFormat) {
        object : Task.Backgroundable(project, "Exporting ${endpoint.endpoint}", true) {
            private var result: EndpointExporter.Result? = null

            override fun run(indicator: ProgressIndicator) {
                // Booting an application takes as long as it takes; doing it on
                // the event thread would freeze the editor for the duration.
                result = EndpointExporter.export(project, endpoint.endpoint, format, endpoint.method)
            }

            override fun onSuccess() {
                when (val outcome = result) {
                    is EndpointExporter.Result.Exported ->
                        EndpointExporter.openInScratch(project, endpoint.endpoint, format, outcome.content)

                    is EndpointExporter.Result.Failed ->
                        Messages.showErrorDialog(project, outcome.reason, "api:export Failed")

                    null -> Unit
                }
            }
        }.queue()
    }
}
