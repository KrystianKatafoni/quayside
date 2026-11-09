@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.sessions.api.SessionMeta
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** [FileSessionMetaSource] on temp directories: where the transcript is looked for and what is combined. */
class FileSessionMetaSourceTest {
    @TempDir
    lateinit var tmp: Path

    private val conversation = ConversationId("00000000-0000-4000-8000-000000000001")

    private fun work(): Path =
        Files.createDirectories(tmp.resolve("work/my.project")).also {
            Files.writeString(Files.createDirectories(it.resolve(".git")).resolve("HEAD"), "ref: refs/heads/feat/x\n")
        }

    private fun transcript(
        home: Path,
        cwd: Path,
        name: String = "${conversation.value}.jsonl",
    ) {
        val dir =
            Files.createDirectories(
                home.resolve("projects").resolve(cwd.toString().replace(Regex("[^A-Za-z0-9]"), "-")),
            )
        Files.writeString(
            dir.resolve(name),
            """{"type":"assistant","message":{"model":"claude-opus-4-1"}}""" + "\n" + """{"permissionMode":"plan"}""" +
                "\n",
        )
    }

    private fun sourceOf(home: Path?) = FileSessionMetaSource(home, UnconfinedTestDispatcher())

    @Test
    fun `model and mode come from the transcript and the branch from HEAD`() =
        runTest {
            val cwd = work()
            val home = Files.createDirectories(tmp.resolve("claude"))
            transcript(home, cwd)

            val meta = sourceOf(home).read(aRuntimeSession(conversationId = conversation, cwd = cwd))

            meta shouldBe SessionMeta("claude-opus-4-1", "plan", "feat/x")
        }

    @Test
    fun `the transcript directory name replaces every non-alphanumeric character`() =
        runTest {
            val cwd = work()
            val home = Files.createDirectories(tmp.resolve("claude"))
            transcript(home, cwd)

            // "my.project" and the slashes of the path become dashes; a wrong encoding would not find the file.
            Files.isDirectory(
                home.resolve("projects").resolve(cwd.toString().replace(Regex("[^A-Za-z0-9]"), "-")),
            ) shouldBe
                true
            sourceOf(home).read(aRuntimeSession(conversationId = conversation, cwd = cwd)).model shouldBe
                "claude-opus-4-1"
        }

    @Test
    fun `without a claude home only the branch is known`() =
        runTest {
            val cwd = work()

            sourceOf(null).read(aRuntimeSession(conversationId = conversation, cwd = cwd)) shouldBe
                SessionMeta(branch = "feat/x")
        }

    @Test
    fun `without a conversation id there is no transcript`() =
        runTest {
            val cwd = work()
            val home = Files.createDirectories(tmp.resolve("claude"))
            transcript(home, cwd)

            sourceOf(home).read(aRuntimeSession(conversationId = null, cwd = cwd)) shouldBe
                SessionMeta(branch = "feat/x")
        }

    @Test
    fun `a conversation id that is not a plain file name is never used as a path`() =
        runTest {
            val cwd = work()
            val home = Files.createDirectories(tmp.resolve("claude"))
            transcript(home, cwd, name = "x.jsonl")
            Files.writeString(
                home.resolve("projects").resolve("secret.jsonl"),
                """{"permissionMode":"bypassPermissions"}""",
            )

            val meta = sourceOf(home).read(aRuntimeSession(conversationId = ConversationId("../secret"), cwd = cwd))

            meta shouldBe SessionMeta(branch = "feat/x")
        }

    @Test
    fun `a directory outside a repository has no branch`() =
        runTest {
            val cwd = Files.createDirectories(tmp.resolve("plain"))

            sourceOf(null).read(aRuntimeSession(conversationId = conversation, cwd = cwd)) shouldBe SessionMeta()
        }

    @Test
    fun `none knows nothing`() =
        runTest {
            NoSessionMetas.read(aRuntimeSession()) shouldBe SessionMeta()
        }
}
