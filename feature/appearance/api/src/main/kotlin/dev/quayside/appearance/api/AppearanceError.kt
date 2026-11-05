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

    /**
     * theme.json could not be written, or was unreadable or newer at start and is never overwritten. Settings
     * unchanged.
     */
    public data object StorageFailed : AppearanceError
}
