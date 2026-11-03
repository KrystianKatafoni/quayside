package dev.quayside.core.designtokens

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TypographyIdTest {
    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = ["", " ", "Plex", "PLEX", "ple x", "plex_mono", "plex!", "pleX"])
    fun `rejects blank and upper case keys`(key: String) {
        shouldThrow<IllegalArgumentException> { TypographyId(key) }
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = ["plex", "jakarta-jb", "inter-jb", "a1", "7"])
    fun `accepts lower case letters, digits and hyphens`(key: String) {
        TypographyId(key).value shouldBe key
    }
}
