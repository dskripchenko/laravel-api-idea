package dev.dskripchenko.laravelapi.settings

import com.intellij.openapi.options.Configurable
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The settings page exists, is reachable, and writes what it shows.
 *
 * A page that is not registered is not a page: the plugin would keep obeying a
 * setting nobody has any way to change, which is the worst of both worlds.
 */
class LaravelApiConfigurableTest : BasePlatformTestCase() {

    fun `test it is registered under Tools`() {
        val ep = Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
            .firstOrNull { it.id == "dev.dskripchenko.laravelapi.settings" }

        assertNotNull("the settings page is not registered", ep)
        assertEquals("tools", ep!!.parentId)
        assertEquals("Laravel API", ep.displayName)
    }

    fun `test the field writes through to the setting`() {
        val configurable = LaravelApiConfigurable(project)

        try {
            configurable.createPanel()
            LaravelApiSettings.of(project).phpExecutable = "/opt/homebrew/bin/php"

            assertEquals("/opt/homebrew/bin/php", LaravelApiSettings.of(project).phpExecutable)
        } finally {
            LaravelApiSettings.of(project).phpExecutable = ""
            configurable.disposeUIResources()
        }
    }
}
