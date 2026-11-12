package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.SetupStage
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import java.io.IOException
import java.nio.file.Path

/** Runs the disk step [block] of [stage]; an exception becomes [AccountSetupError.DiskFailed] of that stage. */
internal fun <T> stage(
    stage: SetupStage,
    block: () -> T,
): Outcome<T, AccountSetupError> = catching({ AccountSetupError.DiskFailed(stage) }, block)

/** The real path of [path]; the absolute normalized one when it does not exist. */
internal fun realPath(path: Path): Path =
    try {
        path.toRealPath()
    } catch (_: IOException) {
        path.toAbsolutePath().normalize()
    }
