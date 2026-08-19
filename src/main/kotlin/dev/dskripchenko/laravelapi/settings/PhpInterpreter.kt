package dev.dskripchenko.laravelapi.settings

import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import java.io.File

/**
 * Which PHP to run the application's own commands with.
 *
 * Three answers in order, and the order is the point. What the user configured
 * wins outright: a machine with four PHP versions on it has exactly one that
 * this project's `artisan` boots under, and no amount of searching can work out
 * which. Only when nothing is configured is the environment asked.
 */
object PhpInterpreter {

    sealed interface Result {
        /** [configured] distinguishes "you told me" from "I looked". */
        data class Found(val path: String, val configured: Boolean) : Result

        /** Nothing to run, said in words a person can act on. */
        data class Missing(val reason: String) : Result
    }

    fun of(project: Project): Result {
        val configured = LaravelApiSettings.of(project).phpExecutable

        if (configured.isNotBlank()) {
            val file = File(configured)

            return when {
                !file.exists() ->
                    Result.Missing("The configured PHP does not exist: $configured (Settings | Tools | Laravel API)")

                file.isDirectory ->
                    Result.Missing("The configured PHP is a directory: $configured — the path has to name the binary itself")

                !file.canExecute() ->
                    Result.Missing("The configured PHP is not executable: $configured (Settings | Tools | Laravel API)")

                else -> Result.Found(configured, configured = true)
            }
        }

        return detect()
            ?.let { Result.Found(it, configured = false) }
            ?: Result.Missing(
                "No `php` found. Looked at the login shell's PATH and the usual places — " +
                    "set the interpreter in Settings | Tools | Laravel API, or run `php artisan api:lint` " +
                    "in a terminal to see what your shell uses."
            )
    }

    /**
     * Where `php` is, when nobody said.
     *
     * `System.getenv("PATH")` is the obvious answer and the wrong one on macOS:
     * an application started from Finder inherits the launcher's environment,
     * not the shell's, so a perfectly working `php` — ServBay's, Homebrew's,
     * anything under a version manager — is invisible to it. The plugin said
     * "No `php` on PATH" to a developer whose terminal runs `php artisan` all
     * day.
     *
     * `EnvironmentUtil` exists in the platform precisely for this: it reads the
     * login shell's environment once and caches it. The plain environment is
     * kept as a fallback for the case where that fails, and a few usual
     * locations after it, so that a missing PATH is not the end of the road.
     */
    fun detect(): String? {
        val paths = buildList {
            EnvironmentUtil.getValue("PATH")?.let(::add)
            System.getenv("PATH")?.let(::add)
        }
            .flatMap { it.split(File.pathSeparator) }
            .plus(FALLBACK_DIRECTORIES)

        return paths
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, "php") }
            .firstOrNull { it.canExecute() }
            ?.path
    }

    /**
     * The places a Mac keeps PHP when the environment says nothing. Not a
     * substitute for a configured interpreter — a hint of last resort.
     */
    private val FALLBACK_DIRECTORIES = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "/usr/bin",
        "/Applications/ServBay/script/alias",
    )
}
