package dev.quayside.agentruntime.impl

import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The recorded outputs of `claude` (fixtures/cli) hold no home paths and no real UUIDs (AM-311 added `--resume`). */
internal class CliRecordingsAnonymityTest {
    private val root: Path =
        Path.of(checkNotNull(javaClass.getResource("/fixtures/cli")) { "fixtures missing" }.toURI())

    @Test
    fun `recorded CLI outputs are anonymized`() {
        FixtureAnonymity.assertAnonymous(root, FixtureAnonymity.fixtureFiles(root))
    }
}
