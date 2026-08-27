package dev.dskripchenko.laravelapi.ui

import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.ui.JBUI
import java.awt.Insets
import javax.swing.JList

/**
 * A list of things to pick from, with room around the words.
 *
 * Three places in this plugin ask the same question — which endpoint, which
 * format, which version — and all three used to build the popup by hand. The
 * platform's default row for a list of plain strings is padded for a tree in a
 * tool window rather than for a menu: one pixel above and below, two beside,
 * which puts the first letter all but on the border and the rows against each
 * other. On a six-line menu that reads as a rendering fault rather than as a
 * dense layout.
 *
 * So the padding lives here, once, and the three callers get it by asking for
 * their list here instead of from the factory.
 */
object ChoiceList {

    /**
     * Space between a line's text and the edges of its row.
     *
     * Unscaled — [JBUI.insets] multiplies by the display scale, which is the
     * whole reason not to write the numbers into a border by hand.
     *
     * Four above and below rather than one: the rows stop touching, and the
     * first and last gain the same gap from the title bar and from the bottom
     * edge, since the popup sizes itself to the rows. Ten beside rather than
     * two, which is roughly what a menu item elsewhere in the IDE gets.
     */
    val PADDING: Insets
        get() = JBUI.insets(4, 10)

    /**
     * The list, titled and padded, for the caller to finish.
     *
     * Stops short of the callback and of showing it: where a popup belongs on
     * screen is the caller's business — beside the caret, over the gutter icon,
     * in the middle of the window — and that is the one thing these three
     * disagree about.
     */
    fun of(labels: List<String>, title: String): IPopupChooserBuilder<String> =
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(labels)
            .setTitle(title)
            .setRenderer(Padded())

    /**
     * One row: the label, and [PADDING] around it.
     *
     * A renderer rather than a border on the list, so that the selection stripe
     * still covers the full width of the row and the text moves inside it. A
     * border would have inset the stripe too, which is a different look and not
     * the one the rest of the IDE has.
     */
    class Padded : ColoredListCellRenderer<String>() {

        init {
            // Set after the superclass constructor, which writes the platform
            // default into the same field, and to a fresh object because it
            // edits that one in place.
            ipad = PADDING
        }

        override fun customizeCellRenderer(
            list: JList<out String>,
            value: String?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            append(value.orEmpty())
        }
    }
}
