package dev.dskripchenko.laravelapi.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Which host serves the documentation this project describes.
 *
 * The one thing about a deep link that cannot be derived from the source: the
 * version, the controller, the action, the method and the prefix are all
 * written down somewhere in the repository, and the address is not. A checkout
 * is served from `localhost`, from a stand, and from production, by the same
 * code.
 *
 * So: what the user configured, else `APP_URL` from the project's `.env`. The
 * second is right most of the time and free; the first exists because "most of
 * the time" is not "always", and reading the documentation of a staging copy
 * while working on a local checkout is an ordinary thing to want.
 */
object DocsBaseUrl {

    sealed interface Result {
        /** [configured] distinguishes "you told me" from "I read your .env". */
        data class Found(val url: String, val configured: Boolean) : Result

        data class Missing(val reason: String) : Result
    }

    fun of(project: Project): Result {
        val configured = LaravelApiSettings.of(project).docsBaseUrl

        if (configured.isNotBlank()) {
            return if (looksLikeUrl(configured)) {
                Result.Found(configured.trimEnd('/'), configured = true)
            } else {
                Result.Missing(
                    "The configured documentation URL has no scheme: $configured — " +
                        "write it as https://example.com (Settings | Tools | Laravel API)"
                )
            }
        }

        val fromEnv = appUrl(project)
            ?: return Result.Missing(
                "No APP_URL in the project's .env, and no address configured — " +
                    "set one in Settings | Tools | Laravel API."
            )

        return if (looksLikeUrl(fromEnv)) {
            Result.Found(fromEnv.trimEnd('/'), configured = false)
        } else {
            Result.Missing("APP_URL in .env is not a URL: $fromEnv")
        }
    }

    /**
     * `APP_URL` as the `.env` spells it.
     *
     * Read from disk rather than from the index: `.env` is gitignored and not
     * PHP, so nothing indexes it, and it is read once per click on a link.
     */
    fun appUrl(project: Project): String? {
        val env = project.guessProjectDir()?.findChild(".env") ?: return null

        return valueOf(env, "APP_URL")
    }

    private fun valueOf(file: VirtualFile, key: String): String? {
        val text = runCatching { VfsUtilCore.loadText(file) }.getOrNull() ?: return null

        return text.lineSequence()
            .map { it.trim() }
            .filterNot { it.startsWith("#") }
            .mapNotNull { line ->
                val name = line.substringBefore('=', missingDelimiterValue = "").trim()
                if (name != key) return@mapNotNull null

                line.substringAfter('=', missingDelimiterValue = "").trim().trim('"', '\'')
            }
            // The last assignment wins, as it does when the file is read by
            // anything else: a key set twice is settled by the second line.
            .lastOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun looksLikeUrl(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
}
