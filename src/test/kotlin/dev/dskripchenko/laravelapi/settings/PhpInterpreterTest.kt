package dev.dskripchenko.laravelapi.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Choosing an interpreter.
 *
 * The setting exists because searching cannot answer the question on a machine
 * with several PHP versions, so the one thing these tests are really about is
 * that a configured path is obeyed — including when obeying it means failing
 * with a clear reason instead of quietly falling back to some other PHP and
 * running the application under a version it was never meant for.
 */
class PhpInterpreterTest : BasePlatformTestCase() {

    private fun settings() = LaravelApiSettings.of(project)

    override fun tearDown() {
        try {
            settings().phpExecutable = ""
        } finally {
            super.tearDown()
        }
    }

    fun `test a configured interpreter is used as given`() {
        val php = File.createTempFile("php", "").apply {
            setExecutable(true)
            deleteOnExit()
        }

        settings().phpExecutable = php.path

        val result = PhpInterpreter.of(project)

        assertTrue(result is PhpInterpreter.Result.Found)
        assertEquals(php.path, (result as PhpInterpreter.Result.Found).path)
        assertTrue("a configured path is not a guess", result.configured)
    }

    fun `test a configured path that does not exist is refused by name`() {
        settings().phpExecutable = "/nowhere/php"

        val result = PhpInterpreter.of(project)

        assertTrue(result is PhpInterpreter.Result.Missing)
        assertTrue(
            "the reason has to name the path and where to change it",
            (result as PhpInterpreter.Result.Missing).reason.contains("/nowhere/php") &&
                result.reason.contains("Laravel API"),
        )
    }

    fun `test a directory is refused rather than executed`() {
        val dir = File.createTempFile("php-dir", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }

        settings().phpExecutable = dir.path

        val result = PhpInterpreter.of(project)

        assertTrue(result is PhpInterpreter.Result.Missing)
        assertTrue((result as PhpInterpreter.Result.Missing).reason.contains("directory"))
    }

    fun `test a file that is not executable is refused`() {
        val php = File.createTempFile("php-plain", "").apply {
            setExecutable(false)
            deleteOnExit()
        }

        settings().phpExecutable = php.path

        val result = PhpInterpreter.of(project)

        assertTrue(result is PhpInterpreter.Result.Missing)
        assertTrue((result as PhpInterpreter.Result.Missing).reason.contains("not executable"))
    }

    /**
     * Without a setting the behaviour is what it was before there was one, and
     * that has to keep working: most projects have exactly one PHP and nobody
     * should have to fill a field to say so.
     */
    fun `test an empty setting falls back to searching`() {
        settings().phpExecutable = ""

        val detected = PhpInterpreter.detect()
        val result = PhpInterpreter.of(project)

        if (detected == null) {
            assertTrue(result is PhpInterpreter.Result.Missing)
            assertTrue((result as PhpInterpreter.Result.Missing).reason.contains("Settings | Tools | Laravel API"))
        } else {
            assertTrue(result is PhpInterpreter.Result.Found)
            assertEquals(detected, (result as PhpInterpreter.Result.Found).path)
            assertFalse("a found interpreter is not a configured one", result.configured)
        }
    }

    fun `test the setting survives a round trip through its state`() {
        settings().phpExecutable = "  /usr/bin/php  "

        val state = settings().state
        val restored = LaravelApiSettings().apply { loadState(state) }

        assertEquals("/usr/bin/php", restored.phpExecutable)
    }
}
