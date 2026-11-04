package dev.quayside.app

import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designtokens.TypographyId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The look the windows start with (FR-A1): `plex` colours and shape, `plex` typography, picked by id not by order. */
class DefaultLookTest {
    private val catalog = appearanceModule().catalog

    private fun ThemeCatalog.plexTheme() = checkNotNull(theme(SchemeId("plex"))) { "plex theme missing" }

    private fun ThemeCatalog.plexTypography() = checkNotNull(typography(TypographyId("plex"))) { "plex font missing" }

    @Test
    fun `default look uses the plex scheme and the plex typography`() {
        val look = catalog.defaultLook()

        look.colors shouldBe catalog.plexTheme().colors
        look.shape shouldBe catalog.plexTheme().shape
        look.typography shouldBe catalog.plexTypography()
    }

    @Test
    fun `default look is picked by id not by position in the catalog`() {
        // Given the same catalog in reverse order, so that plex is neither the first theme nor the first typography
        val reversed = catalog.copy(themes = catalog.themes.reversed(), typographies = catalog.typographies.reversed())

        val look = reversed.defaultLook()

        look.colors shouldBe catalog.plexTheme().colors
        look.typography shouldBe catalog.plexTypography()
    }
}
