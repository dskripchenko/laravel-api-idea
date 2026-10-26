package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Finds where a security scheme is declared.
 *
 * `@security AdminSession` names a key of `getOpenApiSecurityDefinitions()` —
 * or of `getSecuritySchemes()`, its neutral name ([ApiHooks]).
 * When the key is absent the generator writes the name into the spec anyway,
 * under `security:`, referring to a scheme `components.securitySchemes` never
 * defines — a spec that validates and cannot be used to authenticate anything.
 *
 * Running `api:lint` over a real application turned up 848 such references at
 * once, all naming four schemes nobody had declared. That is the shape of this
 * mistake: one omission, multiplied by every action and every panel.
 */
object SecurityLookup {

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
        ApiHooks.apiClasses(project)
            .asSequence()
            .mapNotNull { ApiHooks.SECURITY.effectiveMethod(it) }
            .distinct()
            .flatMap { ApiHooks.declaredKeys(it).asSequence() }
            .toList()
}
