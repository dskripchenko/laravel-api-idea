package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import dev.dskripchenko.laravelapi.validation.MarkupFromRules

/**
 * Writes the `@input` for one validated field.
 *
 * One field, not the whole docblock: the rules say what the field is called and
 * roughly what it holds, and nothing else. What it means is the author's to
 * write, so no description is invented — an empty one is honest, an invented
 * one reads as considered.
 *
 * The tag goes after the last `@input`, where a reader expects it, and existing
 * tags are never touched.
 */
class DocumentFieldFix(
    private val rulePath: String,
    private val rules: List<String>,
) : LocalQuickFix {

    override fun getName(): String = "Document `$rulePath` from its validation rule"

    override fun getFamilyName(): String = "Document a validated field"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = PsiTreeUtil.getParentOfType(descriptor.psiElement, Method::class.java) ?: return
        val docblock = method.docComment ?: return
        val body = MarkupFromRules.tagFor(rulePath, rules) ?: return

        val document = PsiDocumentManager.getInstance(project).getDocument(method.containingFile) ?: return
        val text = docblock.text

        // After the last @input, or before the closing `*/` when there is none
        // to follow.
        val lastInput = text.lastIndexOf("@input")
        val insertAt = if (lastInput >= 0) {
            docblock.textRange.startOffset + text.indexOf('\n', lastInput).let { if (it < 0) text.length else it }
        } else {
            docblock.textRange.startOffset + text.lastIndexOf("*/") - 1
        }

        val indent = indentOf(text)
        document.insertString(insertAt, "\n$indent* @input $body")

        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    /** The docblock's own left margin, so the line lands where the others are. */
    private fun indentOf(text: String): String {
        val line = text.lines().firstOrNull { it.trimStart().startsWith("* @") || it.trimStart().startsWith("*") }
            ?: return "     "

        return line.takeWhile { it == ' ' || it == '\t' }
    }
}
