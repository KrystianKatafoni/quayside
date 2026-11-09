package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.VoicePreview
import dev.quayside.notifications.api.VoicePreviewContract
import java.nio.file.Path

/** [SayVoicePreview] passes the same contract as the fake (ENG-T2), on a scripted [FakeProcessRunner]. */
class SayVoicePreviewContractTest : VoicePreviewContract() {
    private val home = Path.of("/Users/test")
    private var runner = FakeProcessRunner()

    override fun working(): VoicePreview {
        runner = FakeProcessRunner { Outcome.Ok(ProcessResult(0, "", "")) }
        return SayVoicePreview(runner, home)
    }

    override fun failing(): VoicePreview {
        runner = FakeProcessRunner { Outcome.Ok(ProcessResult(1, "secret", "secret")) }
        return SayVoicePreview(runner, home)
    }

    override fun started(): Int = runner.commands.size
}
