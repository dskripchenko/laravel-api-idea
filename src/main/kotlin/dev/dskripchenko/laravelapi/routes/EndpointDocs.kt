package dev.dskripchenko.laravelapi.routes

import com.intellij.openapi.project.Project
import dev.dskripchenko.laravelapi.settings.DocsBaseUrl

/**
 * The documentation page's address for a route map entry.
 *
 * Three things have to come together, and each lives somewhere else: the action
 * is in the Api class, the version is in the module, and the host is in the
 * environment. That is why "open the docs for this endpoint" is not a link
 * anyone can write by hand — and why it is worth building.
 */
object EndpointDocs {

    data class Link(val version: String, val httpMethod: String, val label: String, val url: String)

    /**
     * What every entry in one pass shares.
     *
     * Read once rather than per entry: the version map walks every module in
     * the project and the address is a file on disk. Doing either per gutter
     * icon would be paid for in the editor's responsiveness.
     */
    data class Context(
        val baseUrl: String,
        val config: ApiConfigLookup.Config,
        val versions: Map<String, List<String>>,
    )

    sealed interface Result {
        data class Links(val links: List<Link>) : Result

        /** Nothing to open, and why — for the places that can afford to say so. */
        data class Unavailable(val reason: String) : Result
    }

    fun context(project: Project): Context? {
        val base = DocsBaseUrl.of(project) as? DocsBaseUrl.Result.Found ?: return null

        return Context(base.url, ApiConfigLookup.of(project), ApiVersionLookup.versionsByApi(project))
    }

    /**
     * One link per version and HTTP method.
     *
     * Both plural for the same reason: an Api class exposed under two names is
     * two endpoints, and an action answering GET and POST is two operations on
     * the page. Collapsing either would send half the readers somewhere they
     * did not ask to go.
     */
    fun linksOf(entry: RouteMapLookup.ActionEntry, context: Context): List<Link> =
        ApiVersionLookup.versionsOf(entry, context.versions)
            .filterNot { it in context.config.hiddenVersions }
            .flatMap { version ->
                entry.httpMethods.map { method ->
                    Link(
                        version = version,
                        httpMethod = method,
                        label = "${method.uppercase()}  ${version}.${entry.controllerKey}.${entry.actionKey}",
                        url = DocLink.url(
                            context.baseUrl,
                            context.config,
                            version,
                            entry.controllerKey,
                            entry.actionKey,
                            method,
                        ),
                    )
                }
            }

    /**
     * The same, for a caller that can report a refusal instead of quietly
     * showing nothing.
     *
     * Every empty case is a different problem with a different fix, and
     * "nothing happened" would send a person looking in the wrong place.
     */
    fun of(project: Project, entry: RouteMapLookup.ActionEntry): Result {
        when (val base = DocsBaseUrl.of(project)) {
            is DocsBaseUrl.Result.Missing -> return Result.Unavailable(base.reason)
            is DocsBaseUrl.Result.Found -> Unit
        }

        val context = context(project)
            ?: return Result.Unavailable("No address for the documentation page.")

        val declared = ApiVersionLookup.versionsOf(entry, context.versions)

        if (declared.isEmpty()) {
            return Result.Unavailable(
                "No module names this Api class literally, so there is no version to address it by — " +
                    "a list built at runtime cannot be read from the source."
            )
        }

        val links = linksOf(entry, context)

        if (links.isEmpty()) {
            return Result.Unavailable(
                "Version ${declared.joinToString(", ")} is in `hidden_versions`: the reference page never loads " +
                    "its spec, so an anchor into it would scroll to nothing."
            )
        }

        return Result.Links(links)
    }
}
