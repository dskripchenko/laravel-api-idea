package dev.dskripchenko.laravelapi.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import java.io.File

/**
 * That a menu line keeps its distance from the border.
 *
 * The platform default is one pixel above and below and two beside, which is
 * why this exists at all; a test that only asserted "some padding" would pass
 * against the very thing it was written to replace. So it measures against the
 * default rather than against zero.
 *
 * The second half is a guard on the three callers. Nothing stops a fourth popup
 * being written straight against the factory, and one padded menu beside three
 * cramped ones is worse than four cramped ones — so the factory is spoken to in
 * one file, and this fails if that stops being true.
 */
class ChoiceListTest : BasePlatformTestCase() {

    fun `test a row is padded well past the platform default`() {
        val padding = ChoiceList.PADDING

        // Unscaled 4 and 10, scaled by the display. The assertion is on the
        // relation, not on the number, so this holds on a HiDPI screen too.
        assertTrue("rows are not padded vertically: $padding", padding.top >= 4 && padding.bottom >= 4)
        assertTrue("rows are not padded horizontally: $padding", padding.left >= 10 && padding.right >= 10)
    }

    fun `test the renderer carries the padding and the label`() {
        val renderer = ChoiceList.Padded()
        val list = JBList("Export as Markdown")

        val component = renderer.getListCellRendererComponent(list, "Export as Markdown", 0, false, false)

        assertEquals(ChoiceList.PADDING, renderer.ipad)
        assertEquals("Export as Markdown", renderer.getCharSequence(false).toString())

        // The padding is only worth anything if it reaches the size the list
        // lays the row out to.
        val bare = component.preferredSize.height
        renderer.ipad = java.awt.Insets(0, 0, 0, 0)
        assertTrue(
            "padding does not reach the row height",
            bare > renderer.getListCellRendererComponent(list, "Export as Markdown", 0, false, false)
                .preferredSize.height,
        )
    }

    fun `test every list to choose from is built here`() {
        val sources = File("src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "ChoiceList.kt" }

        val offenders = sources
            .filter { it.readText().replace(Regex("\\s+"), " ").contains("createPopupChooserBuilder") }
            .map { it.name }
            .toList()

        assertEquals("these build a chooser popup outside ChoiceList: $offenders", emptyList<String>(), offenders)
    }
}
