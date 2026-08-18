import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "dev.dskripchenko"
version = "0.1.0"

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
        // The locally installed IDE by default — it is the one the author
        // actually runs, and it saves a gigabyte of download per clean build.
        // CI has no IDE installed, so there the version is pinned through
        // -PphpstormVersion and downloaded.
        val pinned = providers.gradleProperty("phpstormVersion").orNull
        if (pinned != null) {
            phpstorm(pinned)
        } else {
            local(providers.gradleProperty("phpstormPath").orElse("/Applications/PhpStorm.app"))
        }

        // The PHP plugin ships inside PhpStorm, and its API still has to be
        // asked for by id: bundled does not mean on the classpath.
        bundledPlugin("com.jetbrains.php")

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = "Laravel API"

        ideaVersion {
            sinceBuild = "251"
            // Deliberately open-ended. A hard untilBuild means the plugin
            // silently disappears from a freshly updated IDE, which reads to
            // the user as "the plugin is broken" rather than "not verified
            // yet".
            untilBuild = provider { null }
        }
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
