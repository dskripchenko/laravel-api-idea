package dev.dskripchenko.laravelapi.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import dev.dskripchenko.laravelapi.settings.PhpInterpreter

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

        val php = when (val interpreter = PhpInterpreter.of(project)) {
            is PhpInterpreter.Result.Found -> interpreter.path
            is PhpInterpreter.Result.Missing -> return Result.Unavailable(interpreter.reason)
        }

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

}
