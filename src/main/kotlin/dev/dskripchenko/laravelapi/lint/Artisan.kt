package dev.dskripchenko.laravelapi.lint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import dev.dskripchenko.laravelapi.settings.PhpInterpreter

/**
 * Running one of the application's own commands.
 *
 * The plugin asks the application rather than reimplementing it — that is the
 * rule `api:lint` established, and exporting an endpoint follows it for the
 * same reason: the docblock-to-OpenAPI pipeline is the package's, and a second
 * copy of it here would drift from the first.
 *
 * The cost is that a PHP interpreter and a working `artisan` have to exist.
 * When they do not, this says so in a sentence rather than failing somewhere
 * deeper.
 */
object Artisan {

    sealed interface Result {
        data class Output(val stdout: String, val stderr: String, val exitCode: Int) : Result

        /** Could not be run at all, and why in words. */
        data class Unavailable(val reason: String) : Result

        /** It ran and did not finish, or could not be started. */
        data class Failed(val reason: String, val output: String) : Result
    }

    /**
     * Whether running a command is worth offering.
     *
     * A file check rather than an index lookup: this is asked while a menu is
     * being built, and the index is not always ready to answer.
     */
    fun isAvailable(project: Project): Boolean = artisanOf(project) != null

    fun run(project: Project, arguments: List<String>, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Result {
        val artisan = artisanOf(project)
            ?: return Result.Unavailable(
                "No artisan in ${project.basePath ?: "the project"} — this is a command of the application."
            )

        val php = when (val interpreter = PhpInterpreter.of(project)) {
            is PhpInterpreter.Result.Found -> interpreter.path
            is PhpInterpreter.Result.Missing -> return Result.Unavailable(interpreter.reason)
        }

        val workingDirectory = artisan.parent?.path
            ?: return Result.Unavailable("The project has no directory to run in.")

        val command = GeneralCommandLine(listOf(php, artisan.path) + arguments)
            .withWorkDirectory(workingDirectory)
            .withCharset(Charsets.UTF_8)

        val output = runCatching {
            CapturingProcessHandler(command).runProcess(timeoutMs)
        }.getOrElse {
            return Result.Failed("Could not start php: ${it.message}", "")
        }

        if (output.isTimeout) {
            return Result.Failed(
                "${arguments.firstOrNull() ?: "the command"} did not finish within ${timeoutMs / 1000}s",
                output.stdout + output.stderr,
            )
        }

        return Result.Output(output.stdout, output.stderr, output.exitCode)
    }

    /**
     * Through the virtual file system rather than `java.io.File`.
     *
     * Not pedantry: the IDE's idea of the project is the VFS, and a check
     * against the real disk answers "no artisan here" in every test fixture and
     * in any setup where the sources are not plain local files.
     */
    fun artisanOf(project: Project): VirtualFile? =
        project.guessProjectDir()?.findChild("artisan")?.takeIf { !it.isDirectory }

    const val DEFAULT_TIMEOUT_MS = 120_000
}
