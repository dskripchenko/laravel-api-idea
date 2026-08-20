package dev.dskripchenko.laravelapi.lint

import com.intellij.openapi.project.Project

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
    private const val TIMEOUT_MS = Artisan.DEFAULT_TIMEOUT_MS

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
    fun isAvailable(project: Project): Boolean = Artisan.isAvailable(project)

    fun run(project: Project): Result {
        val result = Artisan.run(project, listOf("api:lint", "--json"), TIMEOUT_MS)

        val output = when (result) {
            is Artisan.Result.Unavailable -> return Result.Unavailable(result.reason)
            is Artisan.Result.Failed -> return Result.Failed(result.reason, result.output)
            is Artisan.Result.Output -> result
        }

        val text = output.stdout + output.stderr
        val json = LintFinding.extractJson(text)

        if (json.isEmpty()) {
            // A non-zero exit with no report is the application refusing to
            // boot, and its own message is more useful than anything invented
            // here.
            return Result.Failed("api:lint printed no report", text.trim())
        }

        return Result.Report(LintFinding.parse(json), output.exitCode)
    }
}
