package dev.dskripchenko.laravelapi.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.Method
import dev.dskripchenko.laravelapi.LaravelApiProject
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * Ctrl+Click on a name inside the markup.
 *
 * `{UserResponse}` and `@OrderRequest` lead to the key that declares them in
 * `getOpenApiTemplates()`; `[buildInputs]` leads to the controller's own
 * method. Until now the only way to learn whether any of them existed was to
 * open the Api class and read an array by eye — and the answer "it does not"
 * arrived later, as a dangling `$ref` in a published spec.
 *
 * A handler rather than a PsiReference, and not by preference: `PhpDocTagImpl`
 * declares itself a `ContributedReferenceHost` and then overrides
 * `getReferences()` to ignore contributed ones. The providers do run — asking
 * `ReferenceProvidersRegistry` directly returns the reference — but nothing
 * ever asks them through the tag, so Ctrl+Click sees nothing. Going through
 * the goto handler side-steps that entirely; the red squiggle for a name that
 * resolves to nothing is the annotator's job instead.
 */
class DocGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        if (!LaravelApiProject.isEnabled(element.project)) return null

        val tag = PsiTreeUtil.getParentOfType(element, PhpDocTag::class.java, false) ?: return null

        val tagName = tag.name.removePrefix("@")
        if (tagName !in DocTagGrammar.TAGS) return null

        val body = tag.tagValue
        if (body.isEmpty()) return null

        // Where the caret sits inside the tag's body.
        val bodyStart = tag.textRange.startOffset + tag.text.length - body.length
        val inBody = offset - bodyStart
        if (inBody < 0 || inBody > body.length) return null

        return when (val parsed = DocTagGrammar.parse(tagName, body)) {
            is Parsed.TemplateRef ->
                templateTargets(element, parsed.template, parsed.templateRange, inBody)

            is Parsed.ModelRef ->
                templateTargets(element, parsed.model, parsed.modelRange, inBody)

            is Parsed.Response ->
                parsed.templateRange?.let { templateTargets(element, parsed.template!!, it, inBody) }

            is Parsed.Callable ->
                if (inBody in parsed.methodRange) methodTarget(tag, parsed.method) else null

            is Parsed.Security ->
                if (inBody in parsed.schemeRange) {
                    SecurityLookup.findDeclarations(element.project, parsed.scheme)
                        .ifEmpty { null }
                        ?.toTypedArray()
                } else {
                    null
                }

            else -> null
        }
    }

    private fun templateTargets(
        context: PsiElement,
        name: String,
        range: IntRange,
        caretInBody: Int,
    ): Array<PsiElement>? {
        if (caretInBody !in range) return null

        val declarations = TemplateLookup.findDeclarations(context.project, name)

        return declarations.ifEmpty { return null }.toTypedArray()
    }

    /**
     * The package resolves `[method]` against the controller the docblock
     * belongs to and no other, so the search stops there: a same-named method
     * from elsewhere would be a jump to code that never runs.
     */
    private fun methodTarget(tag: PhpDocTag, methodName: String): Array<PsiElement>? {
        val owner = PsiTreeUtil.getParentOfType(tag, com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment::class.java)
            ?.nextPsiSibling as? Method
            ?: return null

        val target = owner.containingClass?.findMethodByName(methodName) ?: return null

        return arrayOf(target)
    }
}
