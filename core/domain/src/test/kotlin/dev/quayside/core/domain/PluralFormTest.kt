package dev.quayside.core.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PluralFormTest {
    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "0,MANY",
        "1,ONE",
        "2,FEW",
        "3,FEW",
        "4,FEW",
        "5,MANY",
        "12,MANY",
        "13,MANY",
        "14,MANY",
        "21,MANY",
        "22,FEW",
        "112,MANY",
        "102,FEW",
        "-2,MANY",
    )
    fun `form follows the Polish rule`(
        count: Int,
        expected: PluralForm,
    ) {
        PluralForm.of(count) shouldBe expected
    }

    @ParameterizedTest(name = "{0} has key {1}")
    @CsvSource("ONE,one", "FEW,few", "MANY,many")
    fun `keys match the property suffixes`(
        form: PluralForm,
        key: String,
    ) {
        form.key shouldBe key
    }
}
