import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.dskripchenko"
version = "0.6.2"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // PhpStorm rather than IntelliJ IDEA: the whole plugin hangs off the
        // PHP plugin's PSI, and that plugin is not shipped with the Community
        // edition.
        //
        // A pinned, downloaded distribution rather than the IDE installed on
        // this machine. Building against the local install saved a gigabyte and
        // cost more than it saved: the descriptor format changed under a
        // PhpStorm update and the build stopped resolving its own dependencies,
        // while CI — which has no IDE — kept working. One source for both is
        // worth the download.
        phpstorm(providers.gradleProperty("phpstormVersion").orElse("2025.2"))

        // The PHP plugin ships inside PhpStorm, and its API still has to be
        // asked for by id: bundled does not mean on the classpath.
        bundledPlugin("com.jetbrains.php")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)

    // Without this, implementing a platform interface makes the compiler emit a
    // delegating override for every default method it has — six of them for
    // ToolWindowFactory alone. The plugin verifier reads those as us calling
    // internal, experimental and deprecated API we never wrote a line of.
    compilerOptions {
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Laravel API"

        // The notes for the version being built, lifted from CHANGELOG.md.
        // Kept in one place on purpose: a listing whose history disagrees with
        // the repository is worse than one with no history at all.
        // project.version explicitly: inside this block a bare `version` is
        // the plugin configuration's own property, and reading it here asks the
        // changelog for a section named after a Gradle provider.
        changeNotes = provider { changeNotesFor(project.version.toString()) }

        ideaVersion {
            sinceBuild = "251"
            // Deliberately open-ended. A hard untilBuild means the plugin
            // silently disappears from a freshly updated IDE, which reads to
            // the user as "the plugin is broken" rather than "not verified
            // yet".
            untilBuild = provider { null }
        }
    }

    publishing {
        // Never a literal: a Marketplace token is a write credential for every
        // plugin the account owns.
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
    }

    signing {
        // Optional. Marketplace signs unsigned uploads itself; signing here
        // proves the archive left this machine untampered. Absent variables
        // simply leave the task without input, and publishing still works.
        certificateChain = providers.environmentVariable("PLUGIN_CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PLUGIN_PRIVATE_KEY")
        password = providers.environmentVariable("PLUGIN_PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            recommended()

            // IntelliJ IDEA Ultimate is checked explicitly: the plugin depends
            // on com.jetbrains.php, which IDEA does not bundle but can install,
            // and "should work there" is not something to tell a user without
            // having run it.
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate, "2025.2")

            // IDEA Community is deliberately absent. The PHP plugin is not
            // published for it — the verifier reports com.jetbrains.php as
            // unresolvable there, and always will — so keeping it in the list
            // would mean a permanently red check on an expected result.
        }

        // Warnings are what the Marketplace shows next to every uploaded build,
        // and they went unnoticed for four releases. Internal and experimental
        // API break without a deprecation cycle, so those fail the check;
        // deprecated API is listed too, since the only two we ever used were
        // both accidental.
        failureLevel = listOf(
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        )
    }
}

tasks {
    test {
        useJUnit()

        // A local IDE installation puts every bundled plugin on the test
        // classpath, and some of them refuse to start headless — Vue's LSP
        // service dies in its initialiser and takes the whole fixture with it.
        // Only what the plugin actually depends on gets loaded.
        systemProperty("idea.load.plugins.id", "com.jetbrains.php,dev.dskripchenko.laravel-api")
        systemProperty("idea.force.use.core.classloader", "true")
    }

    // The task exists to pre-index the Settings dialog so its options are
    // searchable. This plugin adds no settings, so it indexes nothing — and it
    // pays for that by launching a headless IDE on every build.
    buildSearchableOptions {
        enabled = false
    }
}

/**
 * The section of CHANGELOG.md describing [version], as HTML for the listing.
 *
 * Deliberately unforgiving: releasing a version the changelog does not mention
 * fails the build rather than publishing an empty "what's new".
 */
fun changeNotesFor(version: String): String {
    val changelog = file("CHANGELOG.md")
    require(changelog.exists()) { "CHANGELOG.md is missing" }

    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.startsWith("## [$version]") }
    require(start >= 0) { "CHANGELOG.md has no section for $version" }

    val rest = lines.drop(start + 1)
    val end = rest.indexOfFirst { it.startsWith("## ") }
    val body = if (end >= 0) rest.take(end) else rest

    return body.joinToString("\n").trim().let(::markdownToHtml)
}

/** Enough Markdown for a changelog entry: headings, bullets, code and links. */
fun markdownToHtml(markdown: String): String {
    val html = StringBuilder()
    val paragraph = StringBuilder()
    val bullet = StringBuilder()
    var inList = false

    fun inline(text: String): String = text
        .replace(Regex("""\[([^]]+)]\(([^)]+)\)"""), """<a href="$2">$1</a>""")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "<b>$1</b>")
        .replace(Regex("""`([^`]+)`"""), "<code>$1</code>")

    // Markdown wraps prose across lines; HTML does not. Without flushing on a
    // blank line rather than on every line, one paragraph becomes five.
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            html.append("<p>").append(inline(paragraph.toString().trim())).append("</p>")
            paragraph.setLength(0)
        }
    }

    fun flushBullet() {
        if (bullet.isNotEmpty()) {
            html.append("<li>").append(inline(bullet.toString().trim())).append("</li>")
            bullet.setLength(0)
        }
    }

    fun closeList() {
        flushBullet()
        if (inList) {
            html.append("</ul>")
            inList = false
        }
    }

    for (raw in markdown.lines()) {
        val line = raw.trim()

        when {
            line.startsWith("- ") -> {
                flushParagraph()
                flushBullet()
                if (!inList) {
                    html.append("<ul>")
                    inList = true
                }
                bullet.append(line.removePrefix("- "))
            }

            line.startsWith("### ") -> {
                flushParagraph()
                closeList()
                html.append("<h4>").append(inline(line.removePrefix("### "))).append("</h4>")
            }

            line.isEmpty() -> {
                flushParagraph()
                closeList()
            }

            // A wrapped continuation of whatever is open.
            inList -> bullet.append(" ").append(line)

            else -> paragraph.append(" ").append(line)
        }
    }

    flushParagraph()
    closeList()

    return html.toString()
}
