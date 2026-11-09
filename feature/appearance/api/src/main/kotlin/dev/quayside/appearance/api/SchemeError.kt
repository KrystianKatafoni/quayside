package dev.quayside.appearance.api

/** Why [AppearanceApi.addScheme], [AppearanceApi.previewScheme] or [AppearanceApi.removeScheme] refused; fixed categories. */
public sealed interface SchemeError {
    /** A theme with this id (built-in or custom) is already in the catalog. */
    public data class Taken(
        val id: SchemeId,
    ) : SchemeError

    /** The derived tokens break NFR-6; [problems] are the violated rules as `label value < limit`. */
    public data class LowContrast(
        val problems: List<String>,
    ) : SchemeError

    /** [AppearanceApi.removeScheme]: [id] is not a custom scheme (unknown, or a built-in theme). */
    public data class NotFound(
        val id: SchemeId,
    ) : SchemeError

    /** [AppearanceApi.removeScheme]: [id] is the saved theme or the day or night theme; choose another first. */
    public data class InUse(
        val id: SchemeId,
    ) : SchemeError

    /** theme.json could not be written, or was unreadable or newer at start and is never overwritten. */
    public data object StorageFailed : SchemeError
}
