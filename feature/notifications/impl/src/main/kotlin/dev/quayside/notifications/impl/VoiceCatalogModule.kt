package dev.quayside.notifications.impl

import dev.quayside.core.process.ProcessRunner
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoicePreview
import java.nio.file.Path

/** [VoiceCatalog] on `/usr/bin/say -v ?` run through [process] in [workDir] (the home directory); timeout 10 s. */
public fun voiceCatalogModule(
    process: ProcessRunner,
    workDir: Path,
): VoiceCatalog = SayVoiceCatalog(process, workDir)

/**
 * [VoicePreview] on `/usr/bin/say` run through [process] in [workDir] (the home directory); timeout 60 s (as
 * `MacSayAdapter`), no system-voice retry.
 */
public fun voicePreviewModule(
    process: ProcessRunner,
    workDir: Path,
): VoicePreview = SayVoicePreview(process, workDir)
