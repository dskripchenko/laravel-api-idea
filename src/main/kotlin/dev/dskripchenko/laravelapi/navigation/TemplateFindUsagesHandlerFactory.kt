package dev.dskripchenko.laravelapi.navigation

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.LaravelApiProject

/**
 * Find Usages on a template declaration.
 *
 * Standing on `'DraftPrintResult'` in `getOpenApiTemplates()`, the question is
 * always the same — is anyone still using this, can it go. Without this the
 * answer comes from a text search, which also finds the word in prose.
 *
 * A handler rather than references, for the reason recorded in the README:
 * `PhpDocTagImpl` declares itself a reference host and then ignores contributed
 * references, so nothing in the docblock can be a reference to anything.
 */
class TemplateFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean =
        LaravelApiProject.isEnabled(element.project) && TemplateUsages.isTemplateDeclaration(element)

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        if (element !is StringLiteralExpression) return null

        return object : FindUsagesHandler(element) {
            override fun processElementUsages(
                target: PsiElement,
                processor: Processor<in UsageInfo>,
                options: FindUsagesOptions,
            ): Boolean {
                for (tag in TemplateUsages.find(target.project, element.contents)) {
                    if (!processor.process(UsageInfo(tag))) return false
                }

                return true
            }
        }
    }
}
