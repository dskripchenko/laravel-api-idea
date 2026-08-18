package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ConstantReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReference
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Reads the route map out of `getMethods()`.
 *
 * The map is an ordinary nested PHP array, and that is the whole problem: to
 * the IDE `'update' => ['action' => 'updateItem']` is two strings, while at
 * runtime it is the only thing tying a URL to a method. Rename the method and
 * nothing objects — the endpoint simply starts answering 404, the same 404 as a
 * mistyped URL.
 *
 * Every shape the package accepts is handled here, because a reader that
 * understands three of the four would quietly ignore the fourth:
 *
 *     'actions' => [
 *         'list',                                   // key and method are one
 *         'show' => 'getById',                      // an alias
 *         'disabled' => false,                      // switched off
 *         'create' => ['action' => 'store', ...],   // spelled out
 *     ]
 */
object RouteMapLookup {

    private const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
    private const val METHODS = "getMethods"

    /**
     * One routable action.
     *
     * [anchor] is the element a gutter icon hangs off — the action's key, or
     * the value itself when the entry has no key.
     */
    data class ActionEntry(
        val controllerKey: String,
        val actionKey: String,
        val methodName: String,
        val controllerFqn: String?,
        val anchor: PsiElement,
    )

    /**
     * Every action this application declares.
     *
     * Classes under `vendor/` are left out, and that is not tidiness: the
     * package itself ships an `example/` directory whose Api classes route
     * controllers named A, B, C and D. Without the filter the endpoint list of
     * a real project opened with `a.a → a()`, `b.b → b()` and four more like
     * them — teaching material presented as the application's own surface.
     */
    fun allActions(project: Project): List<ActionEntry> =
        apiClasses(project)
            .filter { it.containingFile?.virtualFile?.path?.contains("/vendor/") != true }
            .flatMap { actionsOf(it) }

    /** The actions declared by one Api class. */
    fun actionsOf(apiClass: PhpClass): List<ActionEntry> {
        val method = apiClass.findOwnMethodByName(METHODS) ?: return emptyList()
        val root = returnedArray(method) ?: return emptyList()
        val controllers = valueOf(root, "controllers") as? ArrayCreationExpression ?: return emptyList()

        return controllers.hashElements.flatMap { controller ->
            val controllerKey = (controller.key as? StringLiteralExpression)?.contents ?: return@flatMap emptyList()
            val options = controller.value as? ArrayCreationExpression ?: return@flatMap emptyList()
            val controllerFqn = classFqnOf(valueOf(options, "controller"))
            val actions = valueOf(options, "actions") as? ArrayCreationExpression ?: return@flatMap emptyList()

            parseActions(controllerKey, controllerFqn, actions)
        }
    }

    /**
     * The map entries pointing at [method], for the arrow back from the
     * controller.
     */
    fun entriesFor(method: Method): List<ActionEntry> {
        val owner = method.containingClass?.fqn ?: return emptyList()

        return allActions(method.project).filter {
            it.methodName == method.name && it.controllerFqn == owner
        }
    }

    /** The controller method an entry points at, when it exists. */
    fun targetMethod(project: Project, entry: ActionEntry): Method? {
        val fqn = entry.controllerFqn ?: return null

        return PhpIndex.getInstance(project).getClassesByFQN(fqn)
            .firstNotNullOfOrNull { it.findMethodByName(entry.methodName) }
    }

    private fun parseActions(
        controllerKey: String,
        controllerFqn: String?,
        actions: ArrayCreationExpression,
    ): List<ActionEntry> {
        val result = mutableListOf<ActionEntry>()

        // `'list'` — no key at all: the value is both the action and the method.
        for (child in actions.children) {
            if (child is ArrayHashElement) continue
            val literal = PsiTreeUtil.findChildOfType(child, StringLiteralExpression::class.java)
                ?.takeIf { it.parent === child || it === child }
                ?: continue

            result += ActionEntry(controllerKey, literal.contents, literal.contents, controllerFqn, literal)
        }

        for (element in actions.hashElements) {
            val key = element.key as? StringLiteralExpression ?: continue
            val actionKey = key.contents

            when (val value = element.value) {
                // `'disabled' => false` — an instruction to drop an inherited
                // action, not an endpoint.
                is ConstantReference -> if (value.name.equals("false", ignoreCase = true)) continue

                // `'show' => 'getById'` — the value is the method's name.
                is StringLiteralExpression ->
                    result += ActionEntry(controllerKey, actionKey, value.contents, controllerFqn, key)

                // `'create' => ['action' => 'store']` — spelled out; without an
                // explicit `action` the key is the method's name.
                is ArrayCreationExpression -> {
                    val explicit = (valueOf(value, "action") as? StringLiteralExpression)?.contents
                    result += ActionEntry(controllerKey, actionKey, explicit ?: actionKey, controllerFqn, key)
                }

                else -> continue
            }
        }

        return result
    }

    private fun apiClasses(project: Project): Collection<PhpClass> {
        val index = PhpIndex.getInstance(project)
        val found = mutableListOf<PhpClass>()

        // processAllSubclasses rather than getAllSubclasses: the latter is
        // deprecated for walking the whole hierarchy on every call.
        index.processAllSubclasses(BASE_API) { found += it; true }

        return found
    }

    private fun returnedArray(method: Method): ArrayCreationExpression? =
        PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }

    /** The value under a string key of an array literal. */
    private fun valueOf(array: ArrayCreationExpression, key: String): PsiElement? =
        array.hashElements
            .firstOrNull { (it.key as? StringLiteralExpression)?.contents == key }
            ?.value

    /**
     * `SomeController::class` → its fully qualified name.
     *
     * A plain string is accepted too: the package takes whatever names a class,
     * and a map written that way routes exactly the same.
     */
    private fun classFqnOf(element: PsiElement?): String? = when (element) {
        is ClassConstantReference ->
            (element.classReference as? PhpReference)?.fqn

        is StringLiteralExpression ->
            element.contents.let { if (it.startsWith("\\")) it else "\\$it" }

        else -> null
    }
}
