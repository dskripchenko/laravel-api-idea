package dev.dskripchenko.laravelapi.navigation

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import dev.dskripchenko.laravelapi.markup.DocTagGrammar
import dev.dskripchenko.laravelapi.markup.DocTagGrammar.Parsed

/**
 * Who refers to a response template.
 *
 * The question this answers is "can I delete this schema", and today it is
 * answered by a text search: the name in `getOpenApiTemplates()` and the name in
 * a docblock are two unrelated strings as far as the IDE is concerned.
 *
 * The search goes through the word index rather than over every PHP file — the
 * template name is one word, and the index knows which files contain it. What is
 * left is to check that the occurrence is really a tag of ours and really names
 * this template, because `UserResponse` also occurs in prose.
 */
object TemplateUsages {

    private const val TEMPLATES_METHOD = "getOpenApiTemplates"

    /** Every docblock tag naming [name]. */
    fun find(project: Project, name: String): List<PhpDocTag> {
        if (name.isEmpty()) return emptyList()

        val found = mutableListOf<PhpDocTag>()

        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                val tag = PsiTreeUtil.getParentOfType(element, PhpDocTag::class.java, false)
                if (tag != null && referencedName(tag) == name) {
                    found += tag
                }

                true
            },
            GlobalSearchScope.projectScope(project),
            name,
            UsageSearchContext.IN_COMMENTS,
            true,
        )

        return found.distinct()
    }

    /**
     * The template a tag refers to, or null when it refers to none.
     *
     * Parsed rather than matched by substring: `@input string $userResponse`
     * contains the word and refers to nothing.
     */
    fun referencedName(tag: PhpDocTag): String? {
        val tagName = tag.name.removePrefix("@")
        if (tagName !in DocTagGrammar.TAGS) return null

        return when (val parsed = DocTagGrammar.parse(tagName, tag.tagValue)) {
            is Parsed.TemplateRef -> parsed.template
            is Parsed.ModelRef -> parsed.model
            is Parsed.Response -> parsed.template
            else -> null
        }
    }

    /**
     * Whether [element] is the key that declares a template — the only thing
     * worth asking this question about.
     *
     * The top-level keys of `getOpenApiTemplates()` are template names;
     * everything one level down is a field of a template and shares the shape,
     * so depth is what tells them apart.
     */
    fun isTemplateDeclaration(element: PsiElement): Boolean {
        if (element !is StringLiteralExpression) return false

        // Up the tree rather than one step: PHP puts a wrapper node between the
        // key and the hash element. `getKey()` hides it, walking the parents
        // does not — and `element.parent as? ArrayHashElement` is therefore
        // always null, which is how this check silently matched nothing.
        val hash = PsiTreeUtil.getParentOfType(element, ArrayHashElement::class.java) ?: return false
        if (hash.key !== element) return false

        val array = PsiTreeUtil.getParentOfType(hash, ArrayCreationExpression::class.java) ?: return false
        val method = PsiTreeUtil.getParentOfType(array, Method::class.java) ?: return false
        if (method.name != TEMPLATES_METHOD) return false

        // A nested array would put another ArrayCreationExpression between this
        // one and the method; the declaration is the outermost.
        if (PsiTreeUtil.getParentOfType(array, ArrayCreationExpression::class.java) != null) return false

        return PsiTreeUtil.getParentOfType(method, PhpClass::class.java) != null
    }
}
