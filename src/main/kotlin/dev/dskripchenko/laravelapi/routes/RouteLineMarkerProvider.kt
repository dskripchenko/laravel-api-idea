package dev.dskripchenko.laravelapi.routes

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * The two arrows between the route map and the controllers.
 *
 * `'update' => ['action' => 'updateItem']` and
 * `public function updateItem()` are, to the IDE, two unrelated strings in two
 * unrelated files. At runtime they are the only thing tying a URL to code —
 * and when they stop matching, nothing says so: the endpoint answers 404, the
 * same 404 as a mistyped URL.
 *
 * The gutter makes the connection visible in both directions, and its absence
 * just as visible: an action with no arrow points at a method that is not
 * there.
 */
class RouteLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = "Laravel API routes"

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Markers are collected for every leaf in the file, so the cheap checks
        // come before anything that touches the index.
        if (element.firstChild != null) return
        if (!LaravelApiProject.isEnabled(element.project)) return

        mapEntryMarker(element)?.let { result.add(it) }
        controllerMethodMarker(element)?.let { result.add(it) }
    }

    /**
     * From the action's key in `getMethods()` to the method it routes.
     */
    private fun mapEntryMarker(leaf: PsiElement): RelatedItemLineMarkerInfo<*>? {
        val literal = leaf.parent as? StringLiteralExpression ?: return null

        val apiClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return null
        if (!isApiClass(apiClass)) return null

        val entry = RouteMapLookup.actionsOf(apiClass).firstOrNull { it.anchor === literal } ?: return null
        val target = RouteMapLookup.targetMethod(leaf.project, entry) ?: return null

        return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
            .setTarget(target)
            .setTooltipText("${entry.controllerKey}.${entry.actionKey} → ${target.containingClass?.name}::${target.name}()")
            .createLineMarkerInfo(leaf)
    }

    /**
     * And back: from the controller's method to every action that routes it.
     *
     * Several, in the plural, because API versions inherit from one another and
     * the same method is commonly routed by more than one of them.
     */
    private fun controllerMethodMarker(leaf: PsiElement): RelatedItemLineMarkerInfo<*>? {
        val method = leaf.parent as? Method ?: return null
        if (leaf.text != method.name) return null

        val entries = RouteMapLookup.entriesFor(method).ifEmpty { return null }
        val anchors = entries.map { it.anchor }

        return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(anchors)
            .setTooltipText(
                entries.joinToString("\n") { "routed as ${it.controllerKey}.${it.actionKey}" }
            )
            .setPopupTitle("Route map entries")
            .createLineMarkerInfo(leaf)
    }

    private fun isApiClass(phpClass: PhpClass): Boolean =
        generateSequence(phpClass) { it.superClass }
            .any { it.fqn == "\\Dskripchenko\\LaravelApi\\Components\\BaseApi" }
}
