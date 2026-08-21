package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.lint.Artisan

/**
 * A way into the documentation from the route map.
 *
 * The reference page can address a single operation, and nothing outside it
 * could build that address: the version comes from the module, the path from
 * the URI pattern, the tag from the controller key and the method from the map.
 * Assembling those by hand to answer "what does this endpoint look like to
 * whoever consumes it" is enough work that nobody does it, and the docblock
 * gets read instead of the page it renders into.
 *
 * Only the map's own action keys, since the controller's side moved to code
 * vision — a second icon beside the route arrow read as clutter rather than as
 * two facts. Here the trade goes the other way: `getMethods()` is an array of
 * action keys and nothing else, so a hint line above each one would double the
 * height of the thing being read.
 *
 * The icon appears only when a link can actually be built, so its presence
 * means it will work. The cases where it cannot — a version assembled at
 * runtime, a version hidden from the index, no address configured — are
 * explained by the tool window's action instead, which can afford a sentence.
 */
class DocLinkLineMarkerProvider : LineMarkerProvider {

    /**
     * Nothing in the fast pass.
     *
     * Every marker here needs the version map, which walks the modules of the
     * whole project, and the address, which is a file on disk. That is work for
     * the slow pass — where it is done once for a batch of elements rather than
     * once per leaf.
     */
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>,
    ) {
        val project = elements.firstOrNull()?.project ?: return
        if (!LaravelApiProject.isEnabled(project)) return

        val context = EndpointDocs.context(project)
        val versions = context?.versions ?: ApiVersionLookup.versionsByApi(project)
        if (context == null && !Artisan.isAvailable(project)) return

        // Read once for the batch rather than per leaf, which would pay for one
        // walk of the project's Api classes per quoted string in the file.
        val actionsByApi = mutableMapOf<PhpClass, List<RouteMapLookup.ActionEntry>>()

        for (element in elements) {
            if (element.firstChild != null) continue

            val entries = entriesAt(element, actionsByApi)
            if (entries.isEmpty()) continue

            val links = context?.let { ctx -> entries.flatMap { EndpointDocs.linksOf(it, ctx) }.distinctBy { it.url } }
                ?: emptyList()

            val targets = entries.flatMap { entry ->
                ApiVersionLookup.versionsOf(entry, versions)
                    .map { EndpointMenu.Target(entry, it) }
                    .ifEmpty { listOf(EndpointMenu.Target(entry, null)) }
            }

            val items = EndpointMenu.itemsFor(project, targets, links)
            if (items.isEmpty()) continue

            result.add(markerFor(element, items))
        }
    }

    /**
     * The map entry a leaf stands for.
     *
     * The action's key in `getMethods()` only — the controller's end of the
     * same question is answered by the code vision line above the method.
     */
    private fun entriesAt(
        leaf: PsiElement,
        actionsByApi: MutableMap<PhpClass, List<RouteMapLookup.ActionEntry>>,
    ): List<RouteMapLookup.ActionEntry> {
        val literal = leaf.parent as? StringLiteralExpression ?: return emptyList()

        // One marker per literal, not per leaf: a quoted string is three tokens.
        if (literal.firstChild !== leaf) return emptyList()

        val apiClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return emptyList()

        return actionsByApi.getOrPut(apiClass) { RouteMapLookup.actionsOf(apiClass) }
            .filter { it.anchor === literal }
    }

    private fun markerFor(leaf: PsiElement, items: List<EndpointMenu.Item>): LineMarkerInfo<PsiElement> =
        LineMarkerInfo(
            leaf,
            leaf.textRange,
            AllIcons.General.Web,
            { items.joinToString("\n") { item -> item.label } },
            { event, _ ->
                EndpointMenu.show(items, "API") { popup ->
                    popup.show(com.intellij.ui.awt.RelativePoint(event))
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Laravel API" },
        )
}
