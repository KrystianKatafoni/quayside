package dev.quayside.appearance.api

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

/** Behaviour every [AppearanceApi] must have (ENG-T2). Runs on the fake and on the real module. */
public abstract class AppearanceApiContract {
    /** The API under test. */
    protected abstract fun api(): AppearanceApi

    @Test
    public fun `catalog is the same on every read`() {
        val api = api()

        api.catalog shouldBeSameInstanceAs api.catalog
    }

    @Test
    public fun `catalog contains the default scheme and typography`() {
        val catalog = api().catalog

        catalog.theme(ThemeCatalog.DEFAULT_SCHEME).shouldNotBeNull().id shouldBe ThemeCatalog.DEFAULT_SCHEME
        catalog.typography(ThemeCatalog.DEFAULT_TYPOGRAPHY).shouldNotBeNull().id shouldBe
            ThemeCatalog.DEFAULT_TYPOGRAPHY
    }
}
