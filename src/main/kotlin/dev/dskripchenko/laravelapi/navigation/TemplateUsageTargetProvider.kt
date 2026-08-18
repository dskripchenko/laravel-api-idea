package dev.dskripchenko.laravelapi.navigation

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * Makes a template declaration something Find Usages can be invoked on.
 *
 * The missing link, and the reason ⌥F7 answered "Cannot search for usages from
 * this location": before consulting any handler, the platform asks what the
 * caret is *on*, and the answer has to be a named element or a reference
 * target. A key in an array literal is neither. The handler was written, ready
 * and never reached.
 *
 * That this went unnoticed is the same mistake twice over. The test called the
 * handler through its factory — proving the search works — and skipped the step
 * the user actually performs, which is the one that failed.
 */
class TemplateUsageTargetProvider : UsageTargetProvider {

    override fun getTargets(editor: Editor, file: PsiFile): Array<UsageTarget>? {
        val element = file.findElementAt(editor.caretModel.offset)?.parent ?: return null

        return targetsFor(element)
    }

    override fun getTargets(element: PsiElement): Array<UsageTarget>? = targetsFor(element)

    private fun targetsFor(element: PsiElement): Array<UsageTarget>? {
        if (!LaravelApiProject.isEnabled(element.project)) return null
        if (!TemplateUsages.isTemplateDeclaration(element)) return null

        return arrayOf(PsiElement2UsageTargetAdapter(element, true))
    }
}
