package dev.dskripchenko.laravelapi.routes

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * The endpoint the caret is on — from either end of the route map.
 *
 * Both ends, because both are where the question comes up: reading the action
 * in `getMethods()`, and reading the controller method it points at. An answer
 * that only worked from one of them would be remembered as not working.
 */
object EndpointAtCaret {

    private const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"

    /**
     * A structural check, cheap enough for building a menu.
     *
     * It says "the caret is somewhere an endpoint could be", not "there is
     * one": establishing the second walks the Api classes of the whole project,
     * which is not work to do while a popup is opening. The action does that
     * once, when it is actually invoked.
     */
    fun looksLikeOne(file: PsiFile, offset: Int): Boolean {
        val element = file.findElementAt(offset) ?: return false

        if (element.parent is Method) return true

        val literal = element.parent as? StringLiteralExpression ?: return false
        val phpClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return false

        return isApiClass(phpClass)
    }

    /** The map entries the caret stands on, resolved for real. */
    fun resolve(file: PsiFile, offset: Int): List<RouteMapLookup.ActionEntry> {
        val element = file.findElementAt(offset) ?: return emptyList()

        (element.parent as? Method)?.let { return RouteMapLookup.entriesFor(it) }

        val literal = element.parent as? StringLiteralExpression ?: return emptyList()
        val apiClass = PsiTreeUtil.getParentOfType(literal, PhpClass::class.java) ?: return emptyList()

        return RouteMapLookup.actionsOf(apiClass).filter { it.anchor === literal }
    }

    private fun isApiClass(phpClass: PhpClass): Boolean =
        generateSequence(phpClass) { it.superClass }.any { it.fqn == BASE_API }

    /** Kept public for callers that already hold an element rather than an offset. */
    fun offsetOf(element: PsiElement): Int = element.textRange.startOffset
}
