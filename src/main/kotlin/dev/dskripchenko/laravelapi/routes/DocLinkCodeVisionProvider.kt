package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Method
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.lint.Artisan

/**
 * `API` above the controller method — one line, everything the endpoint offers.
 *
 * It was a gutter icon first, next to the route arrow, and two icons on one line
 * read as clutter rather than as two facts. Then it was a link that opened the
 * documentation, which left the export reachable only from a context menu
 * nobody finds. Now it is a label with a list under it: the documentation, and
 * the endpoint taken away as a request in each format a client tool reads.
 *
 * The list holds only what is actually possible, so an absent item is the
 * answer to "why can I not" — see [EndpointMenu].
 *
 * The route map keeps its gutter icon. There the trade goes the other way:
 * `getMethods()` is an array of action keys and nothing else, so a hint line
 * above every one of them would double the height of the thing being read.
 */
class DocLinkCodeVisionProvider : DaemonBoundCodeVisionProvider {

    override val id: String get() = ID

    override val name: String get() = "Laravel API"

    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Top

    // After the author line, which is the one people look for first.
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter("vcs.code.vision"))

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        if (file !is PhpFile) return emptyList()
        if (!LaravelApiProject.isEnabled(file.project)) return emptyList()

        val methods = PsiTreeUtil.findChildrenOfType(file, Method::class.java).ifEmpty { return emptyList() }

        val project = file.project

        // Read once for the file. The version map walks every module in the
        // project, the address is a file on disk, and `artisan` is a lookup in
        // the virtual file system — none of it is work to repeat per method.
        val context = EndpointDocs.context(project)
        val versions = context?.versions ?: ApiVersionLookup.versionsByApi(project)
        val exportable = Artisan.isAvailable(project)

        if (context == null && !exportable) return emptyList()

        val entries = RouteMapLookup.allActions(project)

        // Only the classes the map points at. A project's models and services
        // outnumber its controllers by an order of magnitude, and every one of
        // their methods would otherwise be matched against the whole map.
        val routed = entries.mapNotNull { it.controllerFqn }.toSet()

        return methods.mapNotNull { method ->
            val owner = method.containingClass?.fqn ?: return@mapNotNull null
            if (owner !in routed) return@mapNotNull null

            val mine = entries.filter { it.methodName == method.name && it.controllerFqn == owner }
            if (mine.isEmpty()) return@mapNotNull null

            val links = context?.let { ctx -> mine.flatMap { EndpointDocs.linksOf(it, ctx) }.distinctBy { it.url } }
                ?: emptyList()

            val targets = mine.flatMap { entry ->
                ApiVersionLookup.versionsOf(entry, versions)
                    .map { EndpointMenu.Target(entry, it) }
                    .ifEmpty { listOf(EndpointMenu.Target(entry, null)) }
            }

            val items = EndpointMenu.itemsFor(project, targets, links)
            if (items.isEmpty()) return@mapNotNull null

            method.nameIdentifier?.textRange?.let { range -> range to entryFor(items) }
        }
    }

    private fun entryFor(items: List<EndpointMenu.Item>): CodeVisionEntry =
        ClickableTextCodeVisionEntry(
            LABEL,
            ID,
            { event, _ ->
                EndpointMenu.show(items, "API") { popup ->
                    if (event != null) popup.show(RelativePoint(event)) else popup.showInFocusCenter()
                }
            },
            null,
            LABEL,
            items.joinToString("\n") { it.label },
            emptyList(),
        )

    companion object {
        const val ID = "dev.dskripchenko.laravelapi.docLink"

        /**
         * Deliberately just this. The line sits above every routed method in a
         * controller, and a longer label repeated forty times reads as noise —
         * what it leads to is one click away and listed in the tooltip.
         */
        const val LABEL = "API"
    }
}
