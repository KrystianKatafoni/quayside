package dev.quayside.appearance.api

import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
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
}
