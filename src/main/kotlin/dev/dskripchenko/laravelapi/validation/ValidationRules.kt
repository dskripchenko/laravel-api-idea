package dev.dskripchenko.laravelapi.validation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression
import com.jetbrains.php.lang.psi.elements.ArrayHashElement
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * The rules a method validates its input against — when they can be read at
 * all.
 *
 * Read, never guessed. `array_merge`, `Rule::when`, rules assembled from
 * config or a variable: all of it is invisible here, and a partial reading
 * would produce a complaint about a field that is documented three lines
 * further up in a form this cannot see. On the codebases this was built
 * against, one method in fifty-three writes its rules that way — the rest are
 * plain literals.
 */
object ValidationRules {

    /** One validated field, with the element the rule is written on. */
    data class Rule(
        val path: String,
        val rules: List<String>,
        val anchor: StringLiteralExpression,
    )

    /**
     * The rules of a `$request->validate([...])` inside [scope].
     *
     * An empty list means either "validates nothing" or "validates in a way
     * that cannot be read" — the caller must treat both as "say nothing",
     * which is why they are not distinguished.
     */
    fun of(scope: PsiElement): List<Rule> {
        val call = PsiTreeUtil.findChildrenOfType(scope, MethodReference::class.java)
            .firstOrNull { it.name == "validate" }
            ?: return emptyList()

        val array = call.parameters.firstOrNull() as? ArrayCreationExpression ?: return emptyList()

        return read(array)
    }

    private fun read(array: ArrayCreationExpression): List<Rule> {
        val rules = mutableListOf<Rule>()

        for (element in array.hashElements) {
            val key = element.key as? StringLiteralExpression ?: return emptyList()
            val value = element.value

            val ruleList = when (value) {
                // 'email' => ['required', 'email']
                is ArrayCreationExpression -> literalStrings(value) ?: return emptyList()

                // 'email' => 'required|email'
                is StringLiteralExpression -> value.contents.split('|').map(String::trim)

                // Rule::when(...), a variable, a concatenation — the whole
                // array is discarded rather than half-read: a field missing
                // from a partial reading looks exactly like a field nobody
                // documented.
                else -> return emptyList()
            }

            rules += Rule(key.contents, ruleList, key)
        }

        return rules
    }

    /** The strings of an array, or null if anything in it is not a plain one. */
    private fun literalStrings(array: ArrayCreationExpression): List<String>? {
        val values = mutableListOf<String>()

        for (child in array.children) {
            if (child is ArrayHashElement) return null

            val literal = PsiTreeUtil.findChildOfType(child, StringLiteralExpression::class.java)
                ?.takeIf { it.parent === child || it === child }
                ?: return null

            values += literal.contents
        }

        return values
    }

    /** Whether this element is the key of a rule inside a `validate([...])`. */
    fun ruleKeyAt(element: PsiElement): Boolean {
        if (element !is StringLiteralExpression) return false

        val hash = PsiTreeUtil.getParentOfType(element, ArrayHashElement::class.java) ?: return false
        if (hash.key !== element) return false

        val array = PsiTreeUtil.getParentOfType(hash, ArrayCreationExpression::class.java) ?: return false
        val call = array.parent as? PhpPsiElement ?: return false

        return PsiTreeUtil.getParentOfType(call, MethodReference::class.java)?.name == "validate"
    }
}
