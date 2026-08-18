package dev.dskripchenko.laravelapi.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

/**
 * Creates the controller method an action points at.
 *
 * The other side of the error that is already reported: the plugin says the map
 * leads nowhere, and the obvious next move is to write the method. Doing it by
 * hand means switching files, matching the name exactly — a second, slightly
 * different spelling produces the same 404 the fix was meant to end — and
 * copying the docblock conventions of the neighbours.
 */
class CreateActionMethodFix(
    private val controllerFqn: String,
    private val methodName: String,
    private val actionKey: String,
) : LocalQuickFix {

    override fun getName(): String = "Create method '$methodName()' in ${controllerFqn.substringAfterLast('\\')}"

    override fun getFamilyName(): String = "Create the controller method an action points at"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val controller = PhpIndex.getInstance(project).getClassesByFQN(controllerFqn)
            // A method cannot be written into a dependency, and offering to try
            // would end in a read-only editor rather than a result.
            .firstOrNull { it.containingFile?.virtualFile?.isWritable == true }
            ?: return

        val document = PsiDocumentManager.getInstance(project).getDocument(controller.containingFile) ?: return
        val insertAt = controller.textRange.endOffset - 1

        document.insertString(insertAt, buildMethod(controller))
        PsiDocumentManager.getInstance(project).commitDocument(document)

        reformat(project, controller, insertAt)

        controller.findMethodByName(methodName)?.navigate(true)
    }

    private fun buildMethod(controller: PhpClass): String {
        val security = sharedSecurityTags(controller)
        val body = if (returnsThroughHelper(controller)) "return \$this->success();" else "//"

        val docblock = buildString {
            append("/**\n")
            append(" * ").append(actionKey.replaceFirstChar(Char::uppercase)).append("\n")

            // The neighbours' `@security` is copied because authentication is a
            // property of the controller, not of one action. `@response` is not:
            // it names a template describing *this* answer, and borrowing one
            // from a sibling would document a body this method does not return.
            if (security.isNotEmpty()) {
                append(" *\n")
                security.forEach { append(" * @security ").append(it).append("\n") }
            }

            append(" */")
        }

        return "\n$docblock\npublic function $methodName()\n{\n$body\n}\n"
    }

    /**
     * The security schemes every action of this controller already declares.
     *
     * Only the ones they agree on: where the neighbours differ, the answer for
     * a new action is a decision, and guessing it would be worse than leaving
     * the line out.
     */
    private fun sharedSecurityTags(controller: PhpClass): List<String> {
        val perMethod = controller.ownMethods
            .filter { it.access.isPublic && !it.isStatic }
            .mapNotNull { method -> method.docComment?.getTagElementsByName("@security")?.map { it.tagValue.trim() } }
            .filter { it.isNotEmpty() }

        if (perMethod.isEmpty()) return emptyList()

        return perMethod.reduce { shared, tags -> shared.filter { it in tags } }
    }

    /** Whether the controller has a `success()` to return through. */
    private fun returnsThroughHelper(controller: PhpClass): Boolean =
        controller.findMethodByName("success") != null

    private fun reformat(project: Project, controller: PhpClass, around: Int) {
        val element = controller.containingFile.findElementAt(around) ?: return
        val block = PsiTreeUtil.getParentOfType(element, Method::class.java)
            ?: PsiTreeUtil.getParentOfType(element, PhpClass::class.java)
            ?: return

        CodeStyleManager.getInstance(project).reformat(block)
    }
}
