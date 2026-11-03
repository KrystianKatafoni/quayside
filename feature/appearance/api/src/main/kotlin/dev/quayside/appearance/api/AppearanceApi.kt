package dev.quayside.appearance.api

/** Appearance feature contract; AM-804 adds theme, select and update. Thread-safe, never blocks. */
public interface AppearanceApi {
    /** The catalog; the same instance on every read. */
    public val catalog: ThemeCatalog
}
