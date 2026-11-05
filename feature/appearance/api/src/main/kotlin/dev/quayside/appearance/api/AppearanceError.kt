package dev.quayside.appearance.api

import dev.quayside.core.designtokens.TypographyId

/** Why [AppearanceApi.update] refused a change; carries ids only. */
public sealed interface AppearanceError {
    /** The changed settings name a theme outside the catalog. */
    public data class UnknownScheme(
        val id: SchemeId,
    ) : AppearanceError

    /** The changed settings name a typography outside the catalog. */
    public data class UnknownTypography(
        val id: TypographyId,
    ) : AppearanceError
}
