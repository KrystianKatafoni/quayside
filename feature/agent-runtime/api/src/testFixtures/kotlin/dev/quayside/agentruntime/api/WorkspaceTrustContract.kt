package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Behaviour every [WorkspaceTrust] must have (ENG-T2). Runs on [FakeWorkspaceTrust] and on the file-based
 * implementation over a `.claude.json` in a temporary directory.
 */
public abstract class WorkspaceTrustContract {
    /** Returns a [WorkspaceTrust] whose config is valid and trusts nothing yet. */
    public abstract fun create(): WorkspaceTrust

    /** An existing directory; the same one for the whole test. */
    protected abstract val existingDir: Path

    /** A path that does not exist. */
    protected abstract val missingDir: Path

    @Test
    public fun `trusting an existing directory is ok`(): Unit =
        runTest {
            create().trust(existingDir) shouldBe Outcome.Ok(Unit)
        }

    @Test
    public fun `trusting twice is ok`(): Unit =
        runTest {
            val trust = create()
            trust.trust(existingDir) shouldBe Outcome.Ok(Unit)

            trust.trust(existingDir) shouldBe Outcome.Ok(Unit)
        }

    @Test
    public fun `a missing directory gives DIRECTORY_MISSING`(): Unit =
        runTest {
            create().trust(missingDir) shouldBe Outcome.Err(TrustError.DIRECTORY_MISSING)
        }
}
