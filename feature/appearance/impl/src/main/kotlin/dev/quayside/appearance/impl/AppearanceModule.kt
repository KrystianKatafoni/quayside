package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.ThemeCatalog

private class BuiltInAppearance : AppearanceApi {
    override val catalog: ThemeCatalog = ThemeCatalog(BuiltInSchemes.all, BuiltInTypographies.all)
}

/** The only public entry point of this module; called from the composition root in `app`. */
public fun appearanceModule(): AppearanceApi = BuiltInAppearance()
