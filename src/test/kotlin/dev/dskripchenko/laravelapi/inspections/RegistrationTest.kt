package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * That the inspections are registered, described and settable.
 *
 * The point of moving off annotators was the settings screen, the severities
 * and the suppression — all of which come from being registered properly. A
 * `shortName` without a matching file in `inspectionDescriptions/` still works
 * as a check and shows up in the settings tree with an empty description, which
 * is exactly the kind of half-done that nobody notices.
 */
class RegistrationTest : BasePlatformTestCase() {

    private val expected = mapOf(
        "MalformedTag" to "ERROR",
        "UnknownType" to "WARNING",
        "UnknownTemplate" to "ERROR",
        "UnknownSecurityScheme" to "ERROR",
        "MarkupConsistency" to "WARNING",
        "RouteMap" to "ERROR",
        "UndocumentedField" to "WARNING",
    )

    fun `test every inspection is registered under its own name`() {
        val registered = InspectionToolRegistrar.getInstance().createTools()
            .associateBy { it.shortName }

        for ((name, _) in expected) {
            assertTrue("$name is not registered", registered.containsKey(name))
        }
    }

    fun `test every inspection carries a description`() {
        val registered = InspectionToolRegistrar.getInstance().createTools()
            .filter { it.shortName in expected.keys }

        assertEquals(expected.size, registered.size)

        for (tool in registered) {
            val description = tool.loadDescription()
            assertNotNull("${tool.shortName} has no description file", description)
            assertTrue("${tool.shortName} has an empty description", (description ?: "").length > 100)
        }
    }

    fun `test the declared severities are the intended ones`() {
        val registered = InspectionToolRegistrar.getInstance().createTools()
            .filter { it.shortName in expected.keys }

        for (tool in registered) {
            assertEquals(
                "${tool.shortName} is registered at the wrong level",
                expected[tool.shortName],
                tool.defaultLevel.name,
            )
        }
    }

    fun `test they are grouped together, so the settings tree is navigable`() {
        val groups = InspectionToolRegistrar.getInstance().createTools()
            .filter { it.shortName in expected.keys }
            .map { it.groupDisplayName }
            .toSet()

        assertEquals(setOf("Laravel API"), groups)
    }
}
