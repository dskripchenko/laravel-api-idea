package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.intellij.psi.util.PsiTreeUtil
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * A line above the method: where its documentation is.
 *
 * It used to be a second icon in the gutter, next to the route arrow, and two
 * icons on one line read as clutter rather than as two facts. Code vision is
 * where the IDE already puts this kind of statement — "written by", "3 usages",
 * "implemented by" — and it can say what the link is instead of leaving the
 * reader to hover an icon and find out.
 *
 * The route map keeps its gutter icon. A hint line above every action key would
 * double the height of a `getMethods()` array that is nothing but action keys,
 * which is a worse trade than the one being fixed here.
 */
class DocLinkCodeVisionProvider : DaemonBoundCodeVisionProvider {

    override val id: String get() = ID

    override val name: String get() = "Laravel API documentation"

    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Top

    // After the author line, which is the one people look for first.
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter("vcs.code.vision"))

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        if (file !is PhpFile) return emptyList()
        if (!LaravelApiProject.isEnabled(file.project)) return emptyList()

        val methods = PsiTreeUtil.findChildrenOfType(file, Method::class.java).ifEmpty { return emptyList() }

        // Read once for the file: the version map walks every module in the
        // project, and the address is a file on disk.
        val context = EndpointDocs.context(file.project) ?: return emptyList()
        val entries = RouteMapLookup.allActions(file.project)

        // Only the classes the map points at. A project's models and services
        // outnumber its controllers by an order of magnitude, and every one of
        // their methods would otherwise be matched against the whole map.
        val routed = entries.mapNotNull { it.controllerFqn }.toSet()

        return methods.mapNotNull { method ->
            val owner = method.containingClass?.fqn ?: return@mapNotNull null
            if (owner !in routed) return@mapNotNull null

            val links = entries
                .filter { it.methodName == method.name && it.controllerFqn == owner }
                .flatMap { EndpointDocs.linksOf(it, context) }
                .distinctBy { it.url }
                .ifEmpty { return@mapNotNull null }

            method.nameIdentifier?.textRange?.let { range -> range to entryFor(links) }
        }
    }

    private fun entryFor(links: List<EndpointDocs.Link>): CodeVisionEntry {
        val text = if (links.size == 1) {
            "API docs: ${links.first().version}.${links.first().httpMethod.uppercase()}"
        } else {
            "API docs (${links.size})"
        }

        return ClickableTextCodeVisionEntry(
            text,
            ID,
            { event, _ -> open(links, event) },
            null,
            text,
            links.joinToString("\n") { it.url },
            emptyList(),
        )
    }

    /**
     * One link opens; several ask which — a version list and an HTTP method
     * list both multiply, and choosing for the reader is choosing wrong for
     * half of them.
     */
    private fun open(links: List<EndpointDocs.Link>, event: java.awt.event.MouseEvent?) {
        if (links.size == 1) {
            BrowserUtil.browse(links.first().url)

            return
        }

        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(links.map { it.label })
            .setTitle("Open in API Documentation")
            .setItemChosenCallback { chosen ->
                links.firstOrNull { it.label == chosen }?.let { BrowserUtil.browse(it.url) }
            }
            .createPopup()

        if (event != null) popup.show(RelativePoint(event)) else popup.showInFocusCenter()
    }

    companion object {
        const val ID = "dev.dskripchenko.laravelapi.docLink"
    }
}
