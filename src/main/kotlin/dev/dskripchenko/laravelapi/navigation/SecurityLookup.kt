package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Finds where a security scheme is declared.
 *
 * `@security AdminSession` names a key of `getOpenApiSecurityDefinitions()`.
 * When the key is absent the generator writes the name into the spec anyway,
 * under `security:`, referring to a scheme `components.securitySchemes` never
 * defines — a spec that validates and cannot be used to authenticate anything.
 *
 * Running `api:lint` over a real application turned up 848 such references at
 * once, all naming four schemes nobody had declared. That is the shape of this
 * mistake: one omission, multiplied by every action and every panel.
 */
object SecurityLookup {

    private const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
    private const val DEFINITIONS_METHOD = "getOpenApiSecurityDefinitions"

    /** The string literals declaring [name], across every Api class. */
    fun findDeclarations(project: Project, name: String): List<StringLiteralExpression> =
        declarations(project).filter { it.contents == name }

    /** Every scheme name declared in the project. */
    fun allNames(project: Project): Set<String> =
        declarations(project).map { it.contents }.toSet()

    /**
     * Whether the project declares any schemes at all.
     *
     * An application that declares none has evidently not taken the feature up,
     * and painting every `@security` red would be a wall of noise rather than a
     * finding — the plugin would be teaching people to look past it. Once a
     * single scheme exists, an unrecognised name is a real omission and is
     * reported as one.
     */
    fun isInUse(project: Project): Boolean = declarations(project).isNotEmpty()

    private fun declarations(project: Project): List<StringLiteralExpression> =
        apiClasses(project)
            .asSequence()
            .mapNotNull { it.findMethodByName(DEFINITIONS_METHOD) }
            .distinct()
            .flatMap { schemeKeys(it).asSequence() }
            .toList()

    private fun apiClasses(project: Project): Collection<PhpClass> {
        val found = mutableListOf<PhpClass>()
        PhpIndex.getInstance(project).processAllSubclasses(BASE_API) { found += it; true }

        return found
    }

    /**
     * The top-level keys of the returned array: one per scheme. Everything a
     * level down describes the scheme itself — `type`, `name`, `in`.
     */
    private fun schemeKeys(method: Method): List<StringLiteralExpression> {
        val returned = PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }
            ?: return emptyList()

        return returned.hashElements.mapNotNull { it.key as? StringLiteralExpression }
    }
}
