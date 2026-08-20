package dev.dskripchenko.laravelapi.routes

import java.text.Normalizer

/**
 * The address of one endpoint on the project's `/api/doc` page.
 *
 * The page is rendered by Scalar, which addresses an operation by a hash it
 * builds itself:
 *
 *     {version}/tag/{controller}/{METHOD}{path}
 *
 * This mirrors `Dskripchenko\LaravelApi\Services\OpenApi\DocLink`, and mirroring
 * is a thing this plugin otherwise refuses to do — `api:lint` is run rather than
 * reimplemented, precisely so there is one truth about the rules. The exception
 * is deliberate and narrow: this is thirty lines of pure string arithmetic with
 * no application state in it, and the alternative is booting Laravel on every
 * click of a gutter icon. That would make the link slow where it has to be
 * instant, and unavailable in exactly the projects that cannot boot — which is
 * when one reaches for the documentation.
 *
 * Both sides pin the format with tests naming the same URLs. If the format ever
 * moves, they fail together rather than drifting apart quietly.
 *
 * @see ApiConfigLookup for the prefix and the URI pattern, which are read
 */
object DocLink {

    /**
     * Scalar's slug rule.
     *
     * Lowercase, drop everything that is not a letter, a mark, a digit or a
     * separator, collapse the separators into hyphens, trim them off the ends.
     * Unicode letters survive — a Cyrillic name stays a name instead of
     * collapsing into an empty string.
     */
    fun slug(text: String): String {
        val trimmed = text.trim().take(255)
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC).lowercase()

        return normalized
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s_-]"), "")
            .replace(Regex("[\\s_-]+"), "-")
            .trim('-')
    }

    /**
     * The slug a version is addressed by.
     *
     * Dots become hyphens before the rule runs, so `v1.1` reads as `v1-1` rather
     * than `v11` — the package hands the page the same thing, and the two have
     * to agree exactly or the link opens the page and goes nowhere.
     */
    fun documentSlug(version: String): String =
        slug(version.replace('.', '-').replace('/', '-').replace('\\', '-'))

    /** The endpoint's path as the spec spells it — the URI pattern filled in. */
    fun path(uriPattern: String, version: String, controller: String, action: String): String {
        val filled = uriPattern
            .replace("{version}", version)
            .replace("{controller}", controller)
            .replace("{action}", action)

        return "/" + filled.trimStart('/')
    }

    /** The hash of one endpoint, without the leading `#`. */
    fun anchor(
        uriPattern: String,
        version: String,
        controller: String,
        action: String,
        httpMethod: String,
    ): String {
        val path = path(uriPattern, version, controller, action)

        return "${documentSlug(version)}/tag/${slug(controller)}/${httpMethod.uppercase()}$path"
    }

    /**
     * The whole URL, ready to open.
     *
     * [baseUrl] is the application's own address — the one thing here that
     * cannot be derived from the source, since nothing in a repository says
     * which host serves it.
     */
    fun url(
        baseUrl: String,
        config: ApiConfigLookup.Config,
        version: String,
        controller: String,
        action: String,
        httpMethod: String,
    ): String {
        val host = baseUrl.trimEnd('/')
        val prefix = config.prefix.trim('/')
        val anchor = anchor(config.uriPattern, version, controller, action, httpMethod)

        return if (prefix.isEmpty()) "$host/doc#$anchor" else "$host/$prefix/doc#$anchor"
    }
}
