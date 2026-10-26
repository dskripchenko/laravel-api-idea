package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * The static methods on an Api class that declare templates and security
 * schemes — each under two names.
 *
 * laravel-api 6.1 gave `getOpenApiTemplates()` and
 * `getOpenApiSecurityDefinitions()` neutral names, `getSchemaTemplates()` and
 * `getSecuritySchemes()`: the templates describe payloads whatever carries
 * them. An application may override either name. The package reads the neutral
 * one, and its own implementation of it answers with the old one — so the
 * neutral name counts only where the application overrides it.
 */
object ApiHooks {

    const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"

    val TEMPLATES = Hook(neutral = "getSchemaTemplates", legacy = "getOpenApiTemplates")

    val SECURITY = Hook(neutral = "getSecuritySchemes", legacy = "getOpenApiSecurityDefinitions")

    class Hook(val neutral: String, val legacy: String) {

        val names: Set<String> = setOf(neutral, legacy)

        /**
         * The method the package reads on [apiClass], own or inherited: the
         * neutral one when the application overrides it, the old one otherwise.
         */
        fun effectiveMethod(apiClass: PhpClass): Method? {
            val overridden = apiClass.findMethodByName(neutral)
            if (overridden != null && overridden.containingClass?.fqn != BASE_API) return overridden

            return apiClass.findMethodByName(legacy)
        }

        /** The method of [apiClass] itself, under either name, that the package would read. */
        fun ownMethod(apiClass: PhpClass): Method? =
            apiClass.findOwnMethodByName(neutral) ?: apiClass.findOwnMethodByName(legacy)

        /**
         * The name to speak of and to write a new declaration under.
         *
         * Whatever the project already uses — a codebase should not end up with
         * both. A project that uses neither gets the neutral name, but only when
         * the installed package knows it: writing `getSchemaTemplates()` for
         * laravel-api 6.0 declares templates nothing reads.
         */
        fun preferredName(project: Project): String {
            val classes = apiClasses(project)

            if (classes.any { it.findOwnMethodByName(neutral) != null }) return neutral
            if (classes.any { it.findOwnMethodByName(legacy) != null }) return legacy

            val packageKnowsNeutral = PhpIndex.getInstance(project)
                .getClassesByFQN(BASE_API)
                .any { it.findOwnMethodByName(neutral) != null }

            return if (packageKnowsNeutral) neutral else legacy
        }
    }

    /** The functions a version adds to what it inherits with: `array_merge(parent::…(), [...])`. */
    private val MERGING_FUNCTIONS = setOf("array_merge", "array_replace")

    /**
     * The array literals a hook method returns, in order.
     *
     * `return [...]`, or the literals passed to `array_merge()` /
     * `array_replace()` — how a later version adds to what an earlier one
     * declares: `return array_merge(parent::getSchemaTemplates(), [...])`. The
     * call to the parent contributes nothing here: those declarations are found
     * on the parent itself.
     */
    fun returnedArrays(method: Method): List<ArrayCreationExpression> {
        for (statement in PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)) {
            when (val returned = statement.argument) {
                is ArrayCreationExpression -> return listOf(returned)
                is FunctionReference -> if (returned.name?.lowercase() in MERGING_FUNCTIONS) {
                    val arrays = returned.parameters.filterIsInstance<ArrayCreationExpression>()
                    if (arrays.isNotEmpty()) return arrays
                }
            }
        }

        return emptyList()
    }

    /**
     * The top-level keys of what a hook method returns: one per template or
     * scheme. Everything a level down describes the template or the scheme.
     */
    fun declaredKeys(method: Method): List<StringLiteralExpression> =
        returnedArrays(method).flatMap { array -> array.hashElements.mapNotNull { it.key as? StringLiteralExpression } }

    /** Every class extending BaseApi. */
    fun apiClasses(project: Project): Collection<PhpClass> {
        val found = mutableListOf<PhpClass>()

        // processAllSubclasses rather than getAllSubclasses: the latter is
        // deprecated for walking the whole hierarchy on every call, and the
        // plugin verifier says so on every supported IDE build.
        PhpIndex.getInstance(project).processAllSubclasses(BASE_API) { found += it; true }

        return found
    }
}
