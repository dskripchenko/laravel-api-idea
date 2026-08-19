package dev.dskripchenko.laravelapi.inspections

import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Comparing the markup against the validation rules.
 *
 * The cases are printable's and laravel-admin's own, down to the field names:
 * `deliver.email` really was validated and undocumented in a public API, and
 * `ids.*` really has no form in this markup.
 */
class UndocumentedFieldTest : BasePlatformTestCase() {

    private fun enableLaravelApi() {
        myFixture.addFileToProject(
            "vendor/dskripchenko/laravel-api/src/Components/BaseApi.php",
            """
            <?php
            namespace Dskripchenko\LaravelApi\Components;
            abstract class BaseApi {}
            """.trimIndent()
        )
    }

    private fun check(docblock: String, rules: String) {
        myFixture.enableInspections(UndocumentedFieldInspection::class.java)
        myFixture.configureByText(
            "PrintFormController.php",
            """
            <?php
            class PrintFormController
            {
                /**
            $docblock
                 */
                public function create(${'$'}request)
                {
                    ${'$'}data = ${'$'}request->validate([
            $rules
                    ]);

                    return ${'$'}data;
                }
            }
            """.trimIndent()
        )
        myFixture.checkHighlighting(true, false, true)
    }

    fun `test a validated field nobody documented`() {
        enableLaravelApi()
        check(
            docblock = """     * @input string ${'$'}template Template slug""",
            rules = """            'template' => ['required', 'string'],
            <warning descr="`locale` is validated and not documented — the spec will describe a different set of fields from the one this endpoint accepts">'locale'</warning> => ['nullable', 'string'],""",
        )
    }

    fun `test a documented field is left alone`() {
        enableLaravelApi()
        check(
            docblock = """     * @input string ${'$'}template Template slug
     * @input string ?${'$'}locale Template locale""",
            rules = """            'template' => ['required', 'string'],
            'locale' => ['nullable', 'string'],""",
        )
    }

    fun `test nested fields are compared by the markup's own spelling`() {
        enableLaravelApi()
        // `deliver.email` in the rules is `$deliver.email` in the markup — the
        // real case from printable's integration API.
        check(
            docblock = """     * @input object ?${'$'}deliver Email delivery
     * @input string(email) ?${'$'}deliver.email""",
            rules = """            'deliver' => ['nullable', 'array'],
            'deliver.email' => ['required_with:deliver', 'email'],""",
        )
    }

    fun `test an element of a scalar array is never demanded`() {
        enableLaravelApi()
        // `ids.*` has no form in this markup: a tag for it is dropped by the
        // generator without a word. Demanding it would send the author to write
        // a line that vanishes.
        check(
            docblock = """     * @input array ${'$'}ids Identifiers""",
            rules = """            'ids' => ['required', 'array'],
            'ids.*' => ['required'],""",
        )
    }

    fun `test rules that cannot be read are passed over in silence`() {
        enableLaravelApi()
        // Half a reading is worse than none: a field missing from it looks
        // exactly like a field nobody documented.
        check(
            docblock = """     * @input string ${'$'}template Template slug""",
            rules = """            'template' => ['required', 'string'],
            'locale' => ${'$'}this->localeRules(),""",
        )
    }

    fun `test an undocumented method is not nagged field by field`() {
        enableLaravelApi()
        // No @input at all means nobody has documented it yet, and a warning
        // per rule would be noise rather than a finding.
        check(
            docblock = """     * Create a print form""",
            rules = """            'template' => ['required', 'string'],
            'locale' => ['nullable', 'string'],""",
        )
    }

    fun `test it names exactly what was missing in the real API`() {
        enableLaravelApi()
        // printable's print-form/create as it stood before 19.08.2026: the
        // shape of `deliver` lived in prose, and the spec described an object
        // with no fields — email delivery could not be called from its own
        // documentation.
        check(
            docblock = """     * @input string ${'$'}template Template slug
     * @input object ?${'$'}deliver Email delivery: {email!, subject?, message?}""",
            rules = """            'template' => ['required', 'string'],
            'deliver' => ['nullable', 'array'],
            <warning descr="`deliver.email` is validated and not documented — the spec will describe a different set of fields from the one this endpoint accepts">'deliver.email'</warning> => ['required_with:deliver', 'email'],
            <warning descr="`deliver.subject` is validated and not documented — the spec will describe a different set of fields from the one this endpoint accepts">'deliver.subject'</warning> => ['nullable', 'string'],""",
        )
    }

    fun `test the fix writes the tag from the rule`() {
        enableLaravelApi()
        myFixture.enableInspections(UndocumentedFieldInspection::class.java)
        myFixture.configureByText(
            "C.php",
            """
            <?php
            class C
            {
                /**
                 * @input string ${'$'}template Template slug
                 */
                public function create(${'$'}request)
                {
                    return ${'$'}request->validate([
                        'template' => ['required', 'string'],
                        'format' => ['nullable', 'in:link,b64'],
                    ]);
                }
            }
            """.trimIndent()
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.startsWith("Document") }
        assertNotNull("the fix should be offered", fix)
        myFixture.launchAction(fix!!)

        val text = PsiManager.getInstance(project).findFile(myFixture.file.virtualFile)!!.text

        // Type, optionality and the enumeration, all read from the rule; no
        // description invented.
        assertTrue(text, text.contains("@input string ?\$format [link,b64]"))
        assertTrue("the existing tag survives", text.contains("@input string \$template Template slug"))
    }
}
