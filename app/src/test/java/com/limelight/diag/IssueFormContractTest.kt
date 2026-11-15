package com.limelight.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/**
 * The contract between the address the app opens ([IssueUrl]) and the issue form in `.github/ISSUE_TEMPLATE/`
 * (docs/DIAGNOSTICS.md, "Field-id contract of the issue forms"): the form file exists under the name the app sends,
 * and every field the app fills in exists there with a type that takes the value. A released app keeps sending these
 * names, so a rename in the form fails here.
 */
class IssueFormContractTest {
    /** One element of a form's `body`. */
    private data class Field(val type: String, val id: String?, val options: List<String>, val required: Boolean)

    private data class Form(val labels: List<String>, val fields: List<Field>) {
        fun field(id: String) = fields.firstOrNull { it.id == id }
    }

    private val templates: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, ".github/ISSUE_TEMPLATE") }
        .first { it.isDirectory }

    /**
     * Reads the part of GitHub's form schema these forms use: the top-level `labels: [a, b]` and, for each
     * `- type:` element of `body`, its `id`, the `options` of a dropdown and `required: true` under `validations`.
     */
    private fun parse(text: String): Form {
        var labels = emptyList<String>()
        val fields = ArrayList<Field>()
        var type: String? = null
        var id: String? = null
        var options = ArrayList<String>()
        var required = false
        var inOptions = false
        var inValidations = false
        fun close() {
            type?.let { fields += Field(it, id, options, required) }
            type = null; id = null; options = ArrayList(); required = false; inOptions = false; inValidations = false
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = line.length - line.trimStart().length
            val content = line.trim()
            when {
                indent == 0 && content.startsWith("labels:") ->
                    labels = content.substringAfter('[').substringBefore(']').split(',').map { it.trim() }.filter { it.isNotEmpty() }
                indent == 2 && content.startsWith("- type:") -> {
                    close()
                    type = content.substringAfter("- type:").trim()
                }
                type == null -> Unit
                indent == 4 && content.startsWith("id:") -> id = content.substringAfter("id:").trim()
                indent == 4 -> inValidations = content == "validations:"
                indent == 6 -> inOptions = content == "options:"
                inOptions && indent == 8 && content.startsWith("- ") && !content.startsWith("- label:") ->
                    options += content.removePrefix("- ").trim().trim('"')
            }
            if (inValidations && content == "required: true") required = true
        }
        close()
        return Form(labels, fields)
    }

    private fun form(name: String): Form {
        val file = File(templates, name)
        assertTrue("$file must exist: installed apps open it by this name", file.isFile)
        return parse(file.readText())
    }

    private val draft = IssueDraft("t", "w", "d", "b", "s", "l", area = IssueUrl.AREA_OTHER)

    /** What the app sends besides GitHub's own parameters. */
    private fun sentFields(): Set<String> = URI(IssueUrl.build(draft)).rawQuery.split('&')
        .map { it.substringBefore('=') }.toSet() - setOf("template", "title")

    @Test fun theParserReadsAForm() {
        val parsed = parse(
            """
            # comment
            name: X
            labels: [bug, from-app]
            body:
              - type: markdown
                attributes:
                  value: |
                    id: not-a-field
              - type: textarea
                id: what-happened
                attributes:
                  label: What happened?
                validations:
                  required: true
              - type: dropdown
                id: area
                attributes:
                  label: Area
                  options:
                    - Pairing/connection
                    - Other
              - type: checkboxes
                id: confirm
                attributes:
                  options:
                    - label: I read it.
                      required: true
            """.trimIndent(),
        )
        assertEquals(listOf("bug", "from-app"), parsed.labels)
        assertEquals(listOf(null, "what-happened", "area", "confirm"), parsed.fields.map { it.id })
        assertEquals(listOf("markdown", "textarea", "dropdown", "checkboxes"), parsed.fields.map { it.type })
        assertTrue(parsed.field("what-happened")!!.required)
        assertFalse(parsed.field("area")!!.required)
        assertEquals(listOf("Pairing/connection", "Other"), parsed.field("area")!!.options)
        assertFalse("a checkbox is not a required field of the form", parsed.field("confirm")!!.required)
    }

    @Test fun theAppFillsInTheFieldsTheContractNames() {
        assertEquals(setOf("what-happened", "area", "device", "build", "stream-settings", "logs"), sentFields())
    }

    @Test fun everyFieldTheAppFillsInExistsInItsFormWithATypeThatTakesIt() {
        val form = form(IssueUrl.TEMPLATE)
        for (id in sentFields()) {
            val field = form.field(id)
            assertNotNull("${IssueUrl.TEMPLATE} has no field with the id '$id'", field)
            val expected = if (id == "area") setOf("dropdown") else setOf("input", "textarea")
            assertTrue("'$id' is a ${field!!.type}", field.type in expected)
        }
    }

    @Test fun theAreaDropdownOffersBothValuesTheAppSends() {
        val options = form(IssueUrl.TEMPLATE).field("area")!!.options
        assertTrue("$options", IssueUrl.AREA_OTHER in options)
        assertTrue("$options", IssueUrl.AREA_CONNECTION in options)
    }

    @Test fun theFormRequiresNoFieldTheAppLeavesEmpty() {
        val required = form(IssueUrl.TEMPLATE).fields.filter { it.required }.mapNotNull { it.id }.toSet()
        assertEquals("required fields the app does not fill in", emptySet<String>(), required - sentFields())
    }

    @Test fun theFormLabelsTheReportItself() {
        // The address carries no labels parameter (see IssueUrl), so the form is what marks a report as made by the app.
        val labels = form(IssueUrl.TEMPLATE).labels
        assertTrue("$labels", "from-app" in labels)
        assertTrue("$labels", "bug" in labels)
    }

    @Test fun theFormOfEarlierBuildsKeepsTheSameIds() {
        // Builds whose IssueUrl.TEMPLATE was bug_report.yml still open that form.
        val form = form("bug_report.yml")
        for (id in sentFields()) assertNotNull("bug_report.yml has no field with the id '$id'", form.field(id))
        val area = form.field("area")!!
        assertEquals("dropdown", area.type)
        assertTrue(IssueUrl.AREA_OTHER in area.options && IssueUrl.AREA_CONNECTION in area.options)
    }
}
