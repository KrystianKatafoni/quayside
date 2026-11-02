package dev.quayside.notifications.impl

import dev.quayside.core.process.ProcessRunner
import dev.quayside.notifications.api.VoiceCatalog
import java.nio.file.Path

/** [VoiceCatalog] on `/usr/bin/say -v ?` run through [process] in [workDir] (the home directory); timeout 10 s. */
public fun voiceCatalogModule(
    process: ProcessRunner,
    workDir: Path,
): VoiceCatalog = SayVoiceCatalog(process, workDir)
