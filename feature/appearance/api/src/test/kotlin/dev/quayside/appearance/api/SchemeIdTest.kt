package dev.quayside.appearance.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SchemeIdTest {
    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = ["", " ", "Plex", "ple x", "plex_mono", "hc-Light"])
    fun `rejects blank and upper case keys`(key: String) {
        shouldThrow<IllegalArgumentException> { SchemeId(key) }
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = ["plex", "hc-light", "sand-light", "a1"])
    fun `accepts lower case letters, digits and hyphens`(key: String) {
        SchemeId(key).value shouldBe key
    }
}
