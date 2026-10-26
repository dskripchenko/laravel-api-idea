package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Finds where a response template is declared.
 *
 * The declarations live in `getOpenApiTemplates()` — or `getSchemaTemplates()`,
 * its neutral name ([ApiHooks]) — on an Api class: a plain
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

    /**
     * The two the package always provides. They resolve to nothing — there is
     * no source to jump to — but they must not be reported as missing either.
     */
    val BUILT_IN = setOf("Error", "Success")

    /** The string literals declaring [name], across every Api class. */
    fun findDeclarations(project: Project, name: String): List<StringLiteralExpression> =
        ApiHooks.apiClasses(project)
            .asSequence()
            .mapNotNull { ApiHooks.TEMPLATES.effectiveMethod(it) }
            .flatMap { ApiHooks.declaredKeys(it).asSequence() }
            .filter { it.contents == name }
            .toList()

    /** Every template name declared in the project. */
    fun allNames(project: Project): Set<String> =
        ApiHooks.apiClasses(project)
            .asSequence()
            .mapNotNull { ApiHooks.TEMPLATES.effectiveMethod(it) }
            .flatMap { ApiHooks.declaredKeys(it).asSequence() }
            .map { it.contents }
            .toSet() + BUILT_IN

    /** The templates array element a caret sits in, if any. */
    fun hashElementOf(element: PsiElement): ArrayHashElement? =
        PsiTreeUtil.getParentOfType(element, ArrayHashElement::class.java)
}
