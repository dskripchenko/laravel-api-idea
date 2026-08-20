package dev.dskripchenko.laravelapi.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * What this plugin has to be told, which is as little as possible.
 *
 * Inspections keep their settings where every other inspection keeps them, and
 * nothing else here is a preference. The interpreter is the exception, and not
 * a matter of taste: `api:lint` is a command of the application, the
 * application runs on PHP, and there is no way to ask the IDE which PHP that
 * is — the PHP plugin's interpreter configuration is not part of the API it
 * publishes to other plugins.
 *
 * Per project, because the answer is: two projects on one machine routinely
 * want different versions, and a version manager makes "the" PHP a question
 * about a directory rather than about a computer.
 */
@Service(Service.Level.PROJECT)
@State(name = "LaravelApi", storages = [Storage("laravel-api.xml")])
class LaravelApiSettings : PersistentStateComponent<LaravelApiSettings.State> {

    class State {
        /**
         * The interpreter to run `artisan` with. Empty means "find one" — the
         * default, and right for anyone whose shell already has the PHP they
         * want.
         */
        @JvmField
        var phpExecutable: String = ""

        /**
         * Where the reference page lives — `https://api.example.com`, no path.
         *
         * Empty means "read `APP_URL` from the project's `.env`", which is
         * right whenever the documentation is opened against the same
         * application one is running. It is wrong the moment a reader wants the
         * staging copy instead, and nothing in the repository can know that.
         */
        @JvmField
        var docsBaseUrl: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var phpExecutable: String
        get() = state.phpExecutable
        set(value) {
            state.phpExecutable = value.trim()
        }

    var docsBaseUrl: String
        get() = state.docsBaseUrl
        set(value) {
            state.docsBaseUrl = value.trim().trimEnd('/')
        }

    companion object {
        fun of(project: Project): LaravelApiSettings = project.service()
    }
}
