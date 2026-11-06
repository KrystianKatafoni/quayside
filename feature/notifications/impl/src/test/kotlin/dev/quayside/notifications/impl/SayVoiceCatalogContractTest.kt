package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoiceCatalogContract
import dev.quayside.notifications.api.VoiceOption
import java.nio.file.Path

/** [SayVoiceCatalog] passes the same contract as the fake (ENG-T2), fed with `say -v ?` lines. */
class SayVoiceCatalogContractTest : VoiceCatalogContract() {
    private val home = Path.of("/Users/test")

    override fun catalogOf(voices: List<VoiceOption>): VoiceCatalog {
        val output = voices.joinToString("\n") { "${it.id.name.padEnd(PAD)} ${it.locale}    # ${it.sample}".trimEnd() }
        return SayVoiceCatalog(FakeProcessRunner { Outcome.Ok(ProcessResult(0, output, "")) }, home)
    }

    override fun failing(): VoiceCatalog =
        SayVoiceCatalog(FakeProcessRunner { Outcome.Ok(ProcessResult(1, "Zosia pl_PL # secret", "secret")) }, home)

    private companion object {
        const val PAD = 20
    }
}
