package dev.quayside.appearance.api

/** Why [AppearanceApi.addScheme] refused a scheme; fixed categories, never file contents. */
public sealed interface SchemeError {
    /** A theme with this id (built-in or custom) is already in the catalog. */
    public data class Taken(
        val id: SchemeId,
    ) : SchemeError

    /** The derived tokens break NFR-6; [problems] are the violated rules as `label value < limit`. */
    public data class LowContrast(
        val problems: List<String>,
    ) : SchemeError

    /** theme.json could not be written, or was unreadable or newer at start and is never overwritten. */
    public data object StorageFailed : SchemeError
}
