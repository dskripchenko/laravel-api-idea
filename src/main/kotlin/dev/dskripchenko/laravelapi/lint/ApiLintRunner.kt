package dev.dskripchenko.laravelapi.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EnvironmentUtil
import java.io.File

/**
 * Runs the project's own `api:lint`.
 *
 * The command is the source of truth for the rules — twenty-eight of them
 * against the ten this plugin implements — and reimplementing the rest in
 * Kotlin would create a second truth that drifts from the first. That is the
 * mistake this plugin exists to find in other people's code, so the plugin
 * asks the application rather than guessing.
 *
 * The cost is that a PHP interpreter and a working `artisan` have to exist.
 * When they do not, that is said plainly instead of failing somewhere deeper.
 */
object ApiLintRunner {

    /** How long to wait before deciding the command is not coming back. */
    private const val TIMEOUT_MS = 120_000

    sealed interface Result {
        data class Report(val findings: List<LintFinding>, val exitCode: Int) : Result

        /** The command could not be run at all, and why in words. */
        data class Unavailable(val reason: String) : Result

        /** It ran and produced something that is not a report. */
        data class Failed(val reason: String, val output: String) : Result
    }

    /**
     * Whether running it is worth offering.
     *
     * Deliberately a file check rather than an index lookup: this is asked while
     * building a menu, and the index is not always ready to answer — a lesson
     * this plugin learned the hard way with its tool window.
     */
    fun isAvailable(project: Project): Boolean = artisanOf(project) != null

    fun run(project: Project): Result {
        val artisan = artisanOf(project)
            ?: return Result.Unavailable("No artisan in ${project.basePath ?: "the project"} — api:lint is a command of the application.")

        val php = phpExecutable()
            ?: return Result.Unavailable(
                "No `php` found. Looked at the login shell's PATH and the usual places — " +
                    "run `php artisan api:lint` in a terminal to see what your shell uses."
            )

        val workingDirectory = artisan.parent?.path
            ?: return Result.Unavailable("The project has no directory to run in.")

        val command = GeneralCommandLine(php, artisan.path, "api:lint", "--json")
            .withWorkDirectory(workingDirectory)
            .withCharset(Charsets.UTF_8)

        val output = runCatching {
            CapturingProcessHandler(command).runProcess(TIMEOUT_MS)
        }.getOrElse {
            return Result.Failed("Could not start php: ${it.message}", "")
        }

        val text = output.stdout + output.stderr

        if (output.isTimeout) {
            return Result.Failed("api:lint did not finish within ${TIMEOUT_MS / 1000}s", text)
        }

        val json = LintFinding.extractJson(text)
        if (json.isEmpty()) {
            // A non-zero exit with no report is the application refusing to
            // boot, and its own message is more useful than anything invented
            // here.
            return Result.Failed("api:lint printed no report", text.trim())
        }

        return Result.Report(LintFinding.parse(json), output.exitCode)
    }

    /**
     * Through the virtual file system rather than `java.io.File`.
     *
     * Not pedantry: the IDE's idea of the project is the VFS, and a check
     * against the real disk answers "no artisan here" in every test fixture and
     * in any setup where the sources are not plain local files.
     */
    private fun artisanOf(project: Project): VirtualFile? =
        project.guessProjectDir()?.findChild("artisan")?.takeIf { !it.isDirectory }

    /**
     * Where `php` is.
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
    private fun phpExecutable(): String? {
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
