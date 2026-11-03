package dev.quayside.appearance.api

import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ThemeCatalogTest {
    private val plex = TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono")
    private val geist = TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono")

    private fun theme(
        key: String,
        typography: String? = null,
    ): ThemeSpec =
        ThemeSpec(
            id = SchemeId(key),
            name = key,
            mode = ThemeMode.DARK,
            colors = SampleTokens.colors(),
            shape = SampleTokens.shape(),
            defaultTypography = typography?.let(::TypographyId),
        )

    @Test
    fun `rejects duplicate scheme ids`() {
        shouldThrow<IllegalArgumentException> {
            ThemeCatalog(listOf(theme("plex"), theme("paper"), theme("paper")), listOf(plex))
        }
    }

    @Test
    fun `rejects duplicate typography ids`() {
        shouldThrow<IllegalArgumentException> {
            ThemeCatalog(listOf(theme("plex")), listOf(plex, plex.copy(name = "Other")))
        }
    }

    @Test
    fun `rejects missing default typography`() {
        shouldThrow<IllegalArgumentException> {
            ThemeCatalog(listOf(theme("plex")), listOf(geist))
        }
    }

    @Test
    fun `rejects missing default scheme`() {
        shouldThrow<IllegalArgumentException> {
            ThemeCatalog(listOf(theme("paper")), listOf(plex))
        }
    }

    // Defensive: the missing-default checks already reject empty lists, so this does not prove `isNotEmpty` alone.
    @Test
    fun `empty lists are rejected whichever check fires`() {
        shouldThrow<IllegalArgumentException> { ThemeCatalog(emptyList(), listOf(plex)) }
        shouldThrow<IllegalArgumentException> { ThemeCatalog(listOf(theme("plex")), emptyList()) }
    }

    @Test
    fun `rejects default typography outside the catalog`() {
        shouldThrow<IllegalArgumentException> {
            ThemeCatalog(listOf(theme("plex"), theme("paper", typography = "nope")), listOf(plex))
        }
    }

    @Test
    fun `accepts default typography present in the catalog`() {
        val catalog = ThemeCatalog(listOf(theme("plex"), theme("paper", typography = "geist")), listOf(plex, geist))

        catalog.theme(SchemeId("paper"))?.defaultTypography shouldBe geist.id
    }

    @Test
    fun `theme and typography look up by id`() {
        val paper = theme("paper")
        val catalog = ThemeCatalog(listOf(theme("plex"), paper), listOf(plex, geist))

        catalog.theme(SchemeId("paper")) shouldBe paper
        catalog.theme(SchemeId("missing")).shouldBeNull()
        catalog.typography(TypographyId("geist")) shouldBe geist
        catalog.typography(TypographyId("missing")).shouldBeNull()
    }

    @Test
    fun `defaults are plex`() {
        ThemeCatalog.DEFAULT_SCHEME shouldBe SchemeId("plex")
        ThemeCatalog.DEFAULT_TYPOGRAPHY shouldBe TypographyId("plex")
    }
}
