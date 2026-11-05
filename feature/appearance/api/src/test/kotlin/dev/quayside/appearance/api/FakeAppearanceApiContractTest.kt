package dev.quayside.appearance.api

import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The fake passes the same contract as the real module (ENG-T2). */
class FakeAppearanceApiContractTest : AppearanceApiContract() {
    override fun api(): AppearanceApi = FakeAppearanceApi()

    @Test
    fun `fake serves the catalog it was given`() {
        val catalog =
            ThemeCatalog(
                themes =
                    listOf(
                        ThemeSpec(
                            id = SchemeId("plex"),
                            name = "Custom",
                            mode = ThemeMode.LIGHT,
                            colors = SampleTokens.colors(),
                            shape = SampleTokens.shape(),
                            defaultTypography = null,
                        ),
                    ),
                typographies = listOf(TypographySpec(TypographyId("plex"), "Only", "Only Sans", "Only Mono")),
            )

        FakeAppearanceApi(catalog).catalog shouldBeSameInstanceAs catalog
        catalog shouldBe FakeAppearanceApi(catalog).catalog
    }

    @Test
    fun `scripted failure refuses the update and keeps the settings`() =
        runTest {
            val fake = FakeAppearanceApi()
            fake.failure = AppearanceError.UnknownScheme(SchemeId("plex"))

            val result = fake.update { it.copy(uiScale = UiScale.L) }

            result shouldBe Outcome.Err(AppearanceError.UnknownScheme(SchemeId("plex")))
            fake.settings.value shouldBe AppearanceSettings()
            fake.updates shouldBe 0
        }

    @Test
    fun `showcase catalog has distinct colours and two suggested typographies`() {
        val themes = FakeAppearanceApi.showcaseCatalog().themes

        themes.map { it.colors.surfaces.bg }.toSet().size shouldBe themes.size
        themes.map { it.colors.content.accent }.toSet().size shouldBe themes.size
        themes.map { it.defaultTypography?.value } shouldBe listOf(null, "geist", "plex")
    }
}
