package dev.dskripchenko.laravelapi.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * From a template declaration to the docblocks that name it.
 *
 * The arrow the other way round already exists — Ctrl+Click on `{UserResponse}`
 * leads here. This closes the loop: a declaration with no arrow is a schema
 * nobody refers to, which is the answer to "can this go" without asking the
 * question.
 */
class TemplateLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = "Laravel API response templates"

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
    ) {
        // Collected for every leaf in the file, so the cheap checks come first.
        if (element.firstChild != null) return

        val literal = element.parent as? StringLiteralExpression ?: return

        // One marker per literal, not per token: `'UserResponse'` is quote,
        // text, quote, and a marker on each turns one arrow into a popup
        // offering the same target three times. That already happened once, on
        // the route map.
        if (literal.firstChild !== element) return

        if (!LaravelApiProject.isEnabled(element.project)) return
        if (!TemplateUsages.isTemplateDeclaration(literal)) return

        val usages = TemplateUsages.find(element.project, literal.contents).ifEmpty { return }

        result.add(
            NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(usages)
                .setTooltipText(
                    if (usages.size == 1) "referenced once" else "referenced ${usages.size} times"
                )
                .setPopupTitle("Docblocks referring to '${literal.contents}'")
                .createLineMarkerInfo(element)
        )
    }
}
