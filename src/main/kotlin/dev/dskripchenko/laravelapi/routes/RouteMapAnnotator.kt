package dev.dskripchenko.laravelapi.routes

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * The check the whole plugin was worth building for.
 *
 * An action whose controller method does not exist answers **404** at runtime —
 * the same 404 as a mistyped URL, so nothing in the logs tells the two apart.
 * Renaming a method and forgetting the map is a two-second mistake that
 * survives review, deploy and the entire test suite, and surfaces as a bug
 * report from whoever consumes the API.
 *
 * Here it is red while typing.
 */
class RouteMapAnnotator : Annotator {

    private companion object {
        const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is StringLiteralExpression) return
        if (!LaravelApiProject.isEnabled(element.project)) return

        val apiClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java) ?: return
        if (!isApiClass(apiClass)) return

        val entry = RouteMapLookup.actionsOf(apiClass).firstOrNull { it.anchor === element } ?: return

        val controllerFqn = entry.controllerFqn ?: return
        val controllers = PhpIndex.getInstance(element.project).getClassesByFQN(controllerFqn)

        if (controllers.isEmpty()) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "$controllerFqn does not exist — every action of '${entry.controllerKey}' answers 404."
            ).range(element.textRange).create()

            return
        }

        val method = controllers.firstNotNullOfOrNull { it.findMethodByName(entry.methodName) }

        if (method == null) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "${controllers.first().name} has no method '${entry.methodName}()' — " +
                    "this action answers 404, the same 404 as a mistyped URL."
            ).range(element.textRange).create()

            return
        }

        // Present but unreachable: `app()->call()` needs a public, non-static
        // method, and anything else fails exactly like a missing one.
        if (!method.access.isPublic || method.isStatic || method.isAbstract) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "${controllers.first().name}::${entry.methodName}() cannot serve an action — " +
                    "it has to be a public non-static method."
            ).range(element.textRange).create()
        }
    }

    private fun isApiClass(phpClass: PhpClass): Boolean =
        generateSequence(phpClass) { it.superClass }.any { it.fqn == BASE_API }
}
