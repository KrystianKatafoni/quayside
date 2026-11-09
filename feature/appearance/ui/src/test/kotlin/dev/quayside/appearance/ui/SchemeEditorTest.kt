package dev.quayside.appearance.ui

import dev.quayside.appearance.api.FakeAppearanceApi
import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The text model of the scheme editor (FR-A6, plan AM-820): validation and the contrast report. */
class SchemeEditorTest {
    private val mine = SampleSchemes.mine()
    private val valid =
        SchemeEditor
            .blank()
            .with(EditorField.KEY, "mine")
            .with(EditorField.NAME, "Mine")

    @Test
    fun `blank editor starts at graphite and is incomplete`() {
        SchemeEditor.blank().toScheme().shouldBeNull()
        valid.toScheme() shouldBe mine.copy(radius = 6)
    }

    @Test
    fun `fields are trimmed and the mode is kept`() {
        val scheme =
            valid
                .with(EditorField.KEY, " mine ")
                .with(EditorField.NAME, "  Mine  ")
                .with(EditorField.BG, " #111111 ")
                .copy(mode = ThemeMode.LIGHT_HIGH_CONTRAST)
                .toScheme()
                .shouldNotBeNull()

        scheme.id shouldBe SchemeId("mine")
        scheme.name shouldBe "Mine"
        scheme.mode shouldBe ThemeMode.LIGHT_HIGH_CONTRAST
    }

    @ParameterizedTest
    @CsvSource(
        "KEY,Mine,false",
        "KEY,a b,false",
        "KEY,'',false",
        "KEY,a-1,true",
        "NAME,'',false",
        "NAME,'   ',false",
        "RADIUS,-1,false",
        "RADIUS,0,true",
        "RADIUS,24,true",
        "RADIUS,25,false",
        "RADIUS,six,false",
        "BG,#12345,false",
        "BG,#1234567,false",
        "BG,111111,false",
        "BG,#GGGGGG,false",
        "WORK,#22d3ee,true",
        "WORK,#22D3EE80,false",
    )
    fun `each field is validated`(
        field: EditorField,
        value: String,
        accepted: Boolean,
    ) {
        (valid.with(field, value).toScheme() != null) shouldBe accepted
    }

    @Test
    fun `name length limit is 40 characters`() {
        (valid.with(EditorField.NAME, "x".repeat(n = 40)).toScheme() != null) shouldBe true
        (valid.with(EditorField.NAME, "x".repeat(n = 41)).toScheme() != null) shouldBe false
    }

    @Test
    fun `a wrong colour in any of the eight fields makes the editor invalid`() {
        COLOR_FIELDS.forEach { valid.with(it, "#12").toScheme().shouldBeNull() }
    }

    @Test
    fun `report is incomplete, ready, taken or low contrast`() {
        val api = FakeAppearanceApi(FakeAppearanceApi.showcaseCatalog())

        editorReport(SchemeEditor.blank(), api) shouldBe EditorReport.Incomplete
        editorReport(valid, api).shouldBeInstanceOf<EditorReport.Ready>().spec.id shouldBe mine.id
        editorReport(valid.with(EditorField.KEY, "plex"), api) shouldBe
            EditorReport.Refused(SchemeError.Taken(SchemeId("plex")))
        editorReport(valid.with(EditorField.TEXT, "#222222"), api)
            .shouldBeInstanceOf<EditorReport.Refused>()
            .error
            .shouldBeInstanceOf<SchemeError.LowContrast>()
    }
}
