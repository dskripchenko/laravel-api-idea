package dev.dskripchenko.laravelapi.routes

/**
 * How one endpoint reads in the list.
 *
 * Its own file because the thing that was wrong with the list was exactly this
 * — the line said `print-form.create → create()` in a project where two
 * versions declare `print-form`, and no amount of correct lookup fixes a line
 * that omits what distinguishes them.
 */
object EndpointLabel {

    /**
     * `integration.print-form.create  →  create()`.
     *
     * Dotted, because that is the name the endpoint already has: the package
     * registers every route as `api.{version}.{controller}.{action}`, so this
     * line is that name with the prefix dropped — one spelling to search for,
     * whether the search is here, in the route list or in a log.
     *
     * With no version known the prefix is left out entirely rather than filled
     * with a placeholder: a name that sometimes lies is worse than a name that
     * sometimes stops short.
     */
    fun of(entry: RouteMapLookup.ActionEntry, version: String?): String {
        val name = "${entry.controllerKey}.${entry.actionKey}  \u2192  ${entry.methodName}()"

        return if (version.isNullOrBlank()) name else "$version.$name"
    }
}
