package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.routes.RouteMapLookup

/**
 * An action that answers 404 and looks like nothing at all.
 *
 * The route map ties a URL to a method by a pair of strings, and nothing in PHP
 * checks the pair. Rename the method and the endpoint starts answering **404** —
 * the same 404 as a mistyped URL, so the logs cannot tell "this endpoint is
 * gone" from "someone asked for nonsense". The mistake survives review, deploy
 * and the whole test suite, and arrives as a bug report.
 */
class RouteMapInspection : LocalInspectionTool() {

    private companion object {
        const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is StringLiteralExpression) return
                if (!LaravelApiProject.isEnabled(element.project)) return

                val apiClass = PsiTreeUtil.getParentOfType(element, PhpClass::class.java) ?: return
                if (!isApiClass(apiClass)) return

                val entry = RouteMapLookup.actionsOf(apiClass)
                    .firstOrNull { it.anchor === element }
                    ?: return

                val controllerFqn = entry.controllerFqn ?: return
                val controllers = PhpIndex.getInstance(element.project).getClassesByFQN(controllerFqn)

                if (controllers.isEmpty()) {
                    holder.registerProblem(
                        element,
                        "$controllerFqn does not exist — every action of '${entry.controllerKey}' answers 404",
                        ProblemHighlightType.GENERIC_ERROR,
                    )

                    return
                }

                val method = controllers.firstNotNullOfOrNull { it.findMethodByName(entry.methodName) }

                if (method == null) {
                    holder.registerProblem(
                        element,
                        "${controllers.first().name} has no method '${entry.methodName}()' — " +
                            "this action answers 404, the same 404 as a mistyped URL",
                        ProblemHighlightType.GENERIC_ERROR,
                    )

                    return
                }

                // Present and unreachable fails exactly like absent:
                // `app()->call()` needs a public, non-static method.
                if (!method.access.isPublic || method.isStatic || method.isAbstract) {
                    holder.registerProblem(
                        element,
                        "${controllers.first().name}::${entry.methodName}() cannot serve an action — " +
                            "it has to be a public non-static method",
                        ProblemHighlightType.GENERIC_ERROR,
                    )
                }
            }
        }

    private fun isApiClass(phpClass: PhpClass): Boolean =
        generateSequence(phpClass) { it.superClass }.any { it.fqn == BASE_API }
}
