package dev.dskripchenko.laravelapi.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.io.File

/**
 * Settings | Tools | Laravel API.
 *
 * One field, and it exists only because the question cannot be answered any
 * other way: the PHP plugin does not publish its interpreter configuration to
 * other plugins, so "the PHP this project uses" is knowledge this plugin has
 * no route to. Everything else the plugin does is an inspection, and lives
 * where inspections live.
 */
class LaravelApiConfigurable(private val project: Project) :
    BoundSearchableConfigurable("Laravel API", "dev.dskripchenko.laravelapi.settings") {

    override fun createPanel(): DialogPanel {
        val settings = LaravelApiSettings.of(project)

        return panel {
            row("PHP interpreter:") {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.singleFile()
                        .withTitle("Select PHP Interpreter"),
                    project,
                )
                    .bindText(settings::phpExecutable)
                    .comment(
                        "Used to run <code>php artisan api:lint</code>. Leave it empty to search the " +
                            "login shell's PATH — which is right when your terminal already finds the " +
                            "version this project needs, and wrong the moment a machine carries several.",
                    )
                    .resizableColumn()
            }

            row {
                comment(detectedNote())
            }

            row("API documentation URL:") {
                textField()
                    .bindText(settings::docsBaseUrl)
                    .comment(
                        "Where <code>/api/doc</code> is served — <code>https://example.com</code>, host only. " +
                            "Leave it empty to use <code>APP_URL</code> from the project's <code>.env</code>; " +
                            "fill it in to read the documentation of a stand while working on a local checkout.",
                    )
                    .resizableColumn()
            }

            row {
                comment(baseUrlNote())
            }
        }
    }

    /**
     * What the empty field would resolve to, spelled out.
     *
     * Otherwise "leave it empty to search" is advice a person has to test by
     * running the command and reading the failure.
     */
    private fun detectedNote(): String {
        val detected = PhpInterpreter.detect()
            ?: return "Nothing found automatically: without a path here, <code>api:lint</code> cannot be run."

        val version = versionOf(detected)

        return if (version == null) {
            "Found automatically: <code>$detected</code>"
        } else {
            "Found automatically: <code>$detected</code> ($version)"
        }
    }

    /**
     * What the empty field would resolve to — the same courtesy as above.
     *
     * A person filling this in is deciding between "the .env is fine" and "no,
     * point it elsewhere", and that decision needs to know what the .env says.
     */
    private fun baseUrlNote(): String {
        val fromEnv = DocsBaseUrl.appUrl(project)
            ?: return "No <code>APP_URL</code> in the project's <code>.env</code>: without an address here, " +
                "there is nothing to open."

        return "<code>APP_URL</code> in <code>.env</code>: <code>$fromEnv</code>"
    }

    /** `php -v`, first line, trimmed to the version — a sanity check, not a feature. */
    private fun versionOf(path: String): String? = runCatching {
        val process = ProcessBuilder(path, "-v").redirectErrorStream(true).start()
        val line = process.inputStream.bufferedReader().readLine()
        process.destroy()

        line?.split(" ")?.take(2)?.joinToString(" ")
    }.getOrNull()

    override fun getHelpTopic(): String? = null

    companion object {
        /** Kept out of the class name so a rename does not silently orphan the file. */
        const val STORAGE = "laravel-api.xml"

        /** Where the interpreter lives on disk, when one is configured. */
        fun isExecutable(path: String): Boolean = File(path).let { it.isFile && it.canExecute() }
    }
}
