package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Finds where a response template is declared.
 *
 * The declarations live in `getOpenApiTemplates()` on an Api class — a plain
 * PHP array whose string keys are the template names. Nothing in the language
 * connects `@response 200 {UserResponse}` to the key `'UserResponse'`; that
 * connection exists only in the package's runtime, which is why the IDE has to
 * be taught it.
 *
 * Every Api class in the project is searched rather than the one that routes
 * this particular controller: telling which version serves a given method means
 * reading the route map, and a jump that works is worth more than a jump that
 * is provably the right one. A name defined twice is rare, and both targets are
 * offered when it happens.
 */
object TemplateLookup {

    private const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
    private const val TEMPLATES_METHOD = "getOpenApiTemplates"

    /**
     * The two the package always provides. They resolve to nothing — there is
     * no source to jump to — but they must not be reported as missing either.
     */
    val BUILT_IN = setOf("Error", "Success")

    /** The string literals declaring [name], across every Api class. */
    fun findDeclarations(project: Project, name: String): List<StringLiteralExpression> =
        apiClasses(project)
            .asSequence()
            .mapNotNull { it.findMethodByName(TEMPLATES_METHOD) }
            .flatMap { templateKeys(it).asSequence() }
            .filter { it.contents == name }
            .toList()

    /** Every template name declared in the project. */
    fun allNames(project: Project): Set<String> =
        apiClasses(project)
            .asSequence()
            .mapNotNull { it.findMethodByName(TEMPLATES_METHOD) }
            .flatMap { templateKeys(it).asSequence() }
            .map { it.contents }
            .toSet() + BUILT_IN

    private fun apiClasses(project: Project): Collection<PhpClass> =
        PhpIndex.getInstance(project).getAllSubclasses(BASE_API)

    /**
     * The top-level keys of the array the method returns.
     *
     * Only the outermost array: its keys are template names, while everything
     * one level down is a field of a template and shares the shape.
     */
    private fun templateKeys(method: Method): List<StringLiteralExpression> {
        val returned = PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }
            ?: return emptyList()

        return returned.hashElements
            .mapNotNull { it.key as? StringLiteralExpression }
    }

    /** The `getOpenApiTemplates()` array element a caret sits in, if any. */
    fun hashElementOf(element: PsiElement): ArrayHashElement? =
        PsiTreeUtil.getParentOfType(element, ArrayHashElement::class.java)
}
