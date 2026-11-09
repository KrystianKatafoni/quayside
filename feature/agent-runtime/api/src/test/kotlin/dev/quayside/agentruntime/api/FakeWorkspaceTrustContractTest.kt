package dev.quayside.agentruntime.api

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

class FakeWorkspaceTrustContractTest : WorkspaceTrustContract() {
    @TempDir
    lateinit var dir: Path

    override val existingDir: Path get() = dir.resolve("project").createDirectories()
    override val missingDir: Path get() = dir.resolve("nope")

    override fun create(): WorkspaceTrust = FakeWorkspaceTrust(Files::isDirectory)
}
