package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.FunctionReference
import com.jetbrains.php.lang.psi.elements.PhpReturn
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * The three settings that decide what an endpoint's URL looks like.
 *
 * `config/laravel-api.php` is published into the application, and a project may
 * override any of it: the prefix is `api` almost everywhere and `gateway`
 * somewhere, and the URI pattern is a template one is free to reorder. Guessing
 * would produce links that look right and open the wrong page.
 *
 * Absent keys are not absent settings. Laravel merges the published file over
 * the package's own, so a config declaring only `documentation_script` still
 * has a prefix — the package's. That is why every getter falls back to the
 * package's default rather than to nothing.
 */
object ApiConfigLookup {

    const val DEFAULT_PREFIX = "api"
    const val DEFAULT_URI_PATTERN = "{version}/{controller}/{action}"

    private const val CONFIG_FILE = "config/laravel-api.php"

    data class Config(
        val prefix: String,
        val uriPattern: String,
        /**
         * Versions kept off the reference page's index.
         *
         * Their specs stay reachable by direct URL, but the page never loads
         * them — so an anchor into one scrolls to nothing. A link there is worse
         * than no link, because it looks like it worked.
         */
        val hiddenVersions: Set<String>,
    )

    fun of(project: Project): Config {
        val array = configArray(project)

        return Config(
            prefix = stringAt(array, "prefix")?.trim('/') ?: DEFAULT_PREFIX,
            uriPattern = stringAt(array, "uri_pattern") ?: DEFAULT_URI_PATTERN,
            hiddenVersions = stringsAt(array, "hidden_versions"),
        )
    }

    /** The array `config/laravel-api.php` returns, when the project has one. */
    private fun configArray(project: Project): ArrayCreationExpression? {
        val file = project.guessProjectDir()?.findFileByRelativePath(CONFIG_FILE) ?: return null
        val psi = PsiManager.getInstance(project).findFile(file) ?: return null

        return PsiTreeUtil.findChildrenOfType(psi, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }
    }

    /**
     * A literal string under a key — including the default of an `env()` call.
     *
     * `env('LARAVEL_API_PREFIX', 'api')` is read as `api`: the environment is
     * not this plugin's to know, and the fallback is what an application runs
     * with until someone sets the variable. Wrong for the one machine that did;
     * useful for every other, and a link is not a promise.
     */
    private fun stringAt(array: ArrayCreationExpression?, key: String): String? =
        when (val value = valueOf(array ?: return null, key)) {
            is StringLiteralExpression -> value.contents.takeIf { it.isNotBlank() }

            is FunctionReference ->
                if (value.name == "env") {
                    (value.parameters.getOrNull(1) as? StringLiteralExpression)?.contents?.takeIf { it.isNotBlank() }
                } else {
                    null
                }

            else -> null
        }

    private fun stringsAt(array: ArrayCreationExpression?, key: String): Set<String> {
        val list = valueOf(array ?: return emptySet(), key) as? ArrayCreationExpression ?: return emptySet()

        return PsiTreeUtil.findChildrenOfType(list, StringLiteralExpression::class.java)
            .filter { (it.parent as? ArrayHashElement)?.key !== it }
            .map { it.contents }
            .toSet()
    }

    private fun valueOf(array: ArrayCreationExpression, key: String): PsiElement? =
        array.hashElements
            .firstOrNull { (it.key as? StringLiteralExpression)?.contents == key }
            ?.value
}
