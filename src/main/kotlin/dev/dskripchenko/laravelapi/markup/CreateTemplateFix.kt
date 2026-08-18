package dev.dskripchenko.laravelapi.markup

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.PhpReturn

/**
 * Writes a missing template into `getOpenApiTemplates()`.
 *
 * The name is already spelled in the docblock — the whole gesture is switching
 * files, finding the array, guessing the indentation and typing the name a
 * second time, correctly. Getting it wrong the second time produces a template
 * that exists under a slightly different name, which reads as "declared" to a
 * reader and as "missing" to the generator.
 *
 * The declaration it writes is empty on purpose. What the response contains is
 * a decision; where it is declared and how it is spelled is not.
 */
class CreateTemplateFix(private val templateName: String) : LocalQuickFix {

    private companion object {
        const val BASE_API = "\\Dskripchenko\\LaravelApi\\Components\\BaseApi"
        const val METHOD = "getOpenApiTemplates"
    }

    override fun getName(): String = "Declare template '$templateName' in $METHOD()"

    override fun getFamilyName(): String = "Declare a missing response template"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val candidates = apiClasses(project)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor

        when (candidates.size) {
            0 -> return
            1 -> declareIn(project, candidates.first())

            // More than one API version, and only the author knows which one
            // answers this endpoint — the route map would have to be read to
            // guess, and guessing wrong writes the template into the wrong
            // version.
            else -> chooseAndDeclare(project, editor, candidates)
        }
    }

    private fun chooseAndDeclare(project: Project, editor: Editor?, candidates: List<PhpClass>) {
        if (editor == null) return

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(candidates.map { it.fqn })
            .setTitle("Declare '$templateName' in")
            .setItemChosenCallback { chosen ->
                val target = candidates.firstOrNull { it.fqn == chosen } ?: return@setItemChosenCallback
                com.intellij.openapi.command.WriteCommandAction
                    .writeCommandAction(project, target.containingFile)
                    .withName(name)
                    .run<RuntimeException> { declareIn(project, target) }
            }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    private fun declareIn(project: Project, apiClass: PhpClass) {
        val method = apiClass.findOwnMethodByName(METHOD)

        if (method == null) {
            writeWholeMethod(project, apiClass)

            return
        }

        val array = returnedArray(method) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(apiClass.containingFile) ?: return

        // Right after the opening bracket: the position exists whether the
        // array is empty or full, and the reformat below settles the layout.
        val insertAt = array.textRange.startOffset + 1
        document.insertString(insertAt, "\n'$templateName' => [],")

        PsiDocumentManager.getInstance(project).commitDocument(document)
        reformat(project, apiClass, insertAt)
    }

    /**
     * The method may be missing altogether — a template referenced before
     * anything declared one is exactly how that happens.
     */
    private fun writeWholeMethod(project: Project, apiClass: PhpClass) {
        val document = PsiDocumentManager.getInstance(project).getDocument(apiClass.containingFile) ?: return
        val insertAt = apiClass.textRange.endOffset - 1

        document.insertString(
            insertAt,
            """
            public static function $METHOD(): array
            {
            return [
            '$templateName' => [],
            ];
            }
            """.trimIndent() + "\n"
        )

        PsiDocumentManager.getInstance(project).commitDocument(document)
        reformat(project, apiClass, insertAt)
    }

    private fun reformat(project: Project, apiClass: PhpClass, around: Int) {
        val file = apiClass.containingFile
        val element = file.findElementAt(around) ?: return
        val block = PsiTreeUtil.getParentOfType(element, Method::class.java)
            ?: PsiTreeUtil.getParentOfType(element, PhpClass::class.java)
            ?: return

        CodeStyleManager.getInstance(project).reformat(block)
    }

    private fun returnedArray(method: Method): ArrayCreationExpression? =
        PsiTreeUtil.findChildrenOfType(method, PhpReturn::class.java)
            .firstNotNullOfOrNull { it.argument as? ArrayCreationExpression }

    private fun apiClasses(project: Project): List<PhpClass> {
        val found = mutableListOf<PhpClass>()
        PhpIndex.getInstance(project).processAllSubclasses(BASE_API) { phpClass ->
            // Only classes whose source can be edited: a template cannot be
            // declared inside a vendored dependency.
            if (phpClass.containingFile?.virtualFile?.isWritable == true) found += phpClass
            true
        }

        return found
    }
}
