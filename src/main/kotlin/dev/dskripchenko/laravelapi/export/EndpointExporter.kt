package dev.dskripchenko.laravelapi.export

import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ide.scratch.ScratchRootType
import dev.dskripchenko.laravelapi.lint.Artisan
import dev.dskripchenko.laravelapi.routes.DocLink

/**
 * One endpoint, in whatever format a client tool reads.
 *
 * Everything here is a thin wrapper around the application's own
 * `api:export --endpoint`, and deliberately so: the docblock-to-OpenAPI
 * pipeline lives in the package, and a Kotlin copy of it would be a second
 * truth that starts drifting the day either side is touched. What the plugin
 * adds is that the question can be asked from the method one is looking at,
 * rather than from a terminal after working out how the route is named.
 */
object EndpointExporter {

    sealed interface Result {
        data class Exported(val content: String) : Result

        /** Could not be run, or the command refused — its own words. */
        data class Failed(val reason: String) : Result
    }

    /**
     * `version.controller.action` — the same dot notation the package names its
     * routes with, so what is copied out of a log is what the command takes.
     */
    fun endpointName(version: String, controller: String, action: String): String =
        "$version.$controller.$action"

    fun export(
        project: Project,
        endpoint: String,
        format: ExportFormat,
        httpMethod: String? = null,
    ): Result {
        val arguments = buildList {
            add("api:export")
            add("--endpoint=$endpoint")
            add("--format=${format.option}")
            add("--stdout")
            httpMethod?.let { add("--method=$it") }
        }

        return when (val result = Artisan.run(project, arguments)) {
            is Artisan.Result.Unavailable -> Result.Failed(result.reason)

            is Artisan.Result.Failed -> Result.Failed(result.reason)

            is Artisan.Result.Output ->
                if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                    Result.Exported(result.stdout.trim())
                } else {
                    // The command's own message: it knows whether the version
                    // is unknown, the action undocumented, or the application
                    // simply refusing to boot. Anything invented here would be
                    // a worse guess.
                    Result.Failed(
                        (result.stdout + result.stderr).trim().ifBlank {
                            "api:export produced nothing and said nothing (exit ${result.exitCode})"
                        }
                    )
                }
        }
    }

    /**
     * The result, in an editor.
     *
     * A scratch file rather than a dialog or the clipboard: it can be read,
     * edited, saved wherever the collection actually lives, and thrown away
     * without a trace if it was only ever a question.
     */
    fun openInScratch(project: Project, endpoint: String, format: ExportFormat, content: String): VirtualFile? {
        val language = format.languageId?.let { Language.findLanguageByID(it) }
            ?: Language.findLanguageByID("TEXT")
            ?: return null

        val name = "${DocLink.slug(endpoint)}.${format.extension}"

        val file = ScratchRootType.getInstance()
            .createScratchFile(project, name, language, content)
            ?: return null

        FileEditorManager.getInstance(project).openFile(file, true)

        return file
    }
}
