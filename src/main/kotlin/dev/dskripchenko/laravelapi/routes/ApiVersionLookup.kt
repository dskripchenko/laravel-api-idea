package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReference
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Which version an Api class is reached under.
 *
 * The route map says nothing about it: an Api class declares controllers and
 * actions, and the segment a request actually carries — `integration` in
 * `/api/integration/print-form/create` — is decided elsewhere, by the module's
 * `getApiVersionList()`:
 *
 *     return [
 *         ...parent::getApiVersionList(),
 *         'integration' => Integration\V1::class,
 *         'drafts'      => Drafts\V1::class,
 *     ];
 *
 * Two actions can share a controller key and an action key across versions and
 * be different endpoints entirely, so a list that omits the version is a list
 * of ambiguous names.
 *
 * Only literal entries are read. `AdminApiModule` builds its panel versions
 * from a registry resolved at runtime; nothing static can know those names, and
 * inventing one would be worse than admitting there is none.
 */
object ApiVersionLookup {

    private const val BASE_MODULE = "\\Dskripchenko\\LaravelApi\\Components\\BaseModule"
    private const val VERSION_LIST = "getApiVersionList"

    /**
     * Api class FQN → the version names mapped to it.
     *
     * A list rather than one name: nothing stops a module from exposing the
     * same class twice, and a legacy alias next to a current name is a normal
     * thing to find.
     */
    fun versionsByApi(project: Project): Map<String, List<String>> {
        val index = PhpIndex.getInstance(project)
        val modules = mutableListOf<PhpClass>()
        index.processAllSubclasses(BASE_MODULE) { modules += it; true }

        val map = mutableMapOf<String, MutableList<String>>()

        for (module in modules) {
            for ((version, fqn) in entriesOf(module)) {
                val names = map.getOrPut(fqn) { mutableListOf() }
                if (version !in names) names += version
            }
        }

        return map
    }

    /**
     * The versions [entry] answers under — empty when no module names its class
     * literally.
     *
     * A list, and the caller shows one line per name: an Api class exposed
     * twice is reachable at two URLs, and two URLs are two endpoints however
     * much code they share.
     */
    fun versionsOf(entry: RouteMapLookup.ActionEntry, versions: Map<String, List<String>>): List<String> =
        entry.apiFqn?.let { versions[it] } ?: emptyList()

    /** The literal `'name' => SomeApi::class` pairs of one module class. */
    private fun entriesOf(module: PhpClass): List<Pair<String, String>> {
        val method = module.findOwnMethodByName(VERSION_LIST) ?: return emptyList()
        val array = PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }
            ?: return emptyList()

        return array.hashElements.mapNotNull { element ->
            val version = (element.key as? StringLiteralExpression)?.contents ?: return@mapNotNull null
            val fqn = fqnOf(element.value) ?: return@mapNotNull null

            version to fqn
        }
    }

    /**
     * `Integration\V1::class` → its fully qualified name. A plain string is
     * accepted too — the module takes whatever names a class.
     */
    private fun fqnOf(element: com.intellij.psi.PsiElement?): String? = when (element) {
        is ClassConstantReference ->
            (element.classReference as? PhpReference)?.fqn

        is StringLiteralExpression ->
            element.contents.takeIf { it.isNotBlank() }?.let { if (it.startsWith("\\")) it else "\\$it" }

        else -> null
    }
}
