package dev.quayside.core.process

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** [GitBranch] on real temp directories (docs/12: adapters on local resources). */
class GitBranchTest {
    @TempDir
    lateinit var tmp: Path

    private fun repo(
        dir: Path,
        head: String,
    ): Path {
        val git = Files.createDirectories(dir.resolve(".git"))
        Files.writeString(git.resolve("HEAD"), head)
        return dir
    }

    @Test
    fun `branch of a work tree is the ref name`() {
        GitBranch.read(repo(tmp, "ref: refs/heads/main\n")) shouldBe "main"
    }

    @Test
    fun `a branch name keeps its slashes`() {
        GitBranch.read(repo(tmp, "ref: refs/heads/feat/AM-818-meta\n")) shouldBe "feat/AM-818-meta"
    }

    @Test
    fun `a subdirectory finds the repository above it`() {
        repo(tmp, "ref: refs/heads/dev\n")
        val sub = Files.createDirectories(tmp.resolve("a/b"))

        GitBranch.read(sub) shouldBe "dev"
    }

    @Test
    fun `detached head gives the short commit`() {
        GitBranch.read(repo(tmp, "0123456789abcdef0123456789abcdef01234567\n")) shouldBe "0123456"
    }

    @Test
    fun `a linked worktree follows the gitdir file`() {
        val main = tmp.resolve("main")
        val linked = Files.createDirectories(main.resolve(".git/worktrees/wt"))
        Files.writeString(linked.resolve("HEAD"), "ref: refs/heads/wt-branch\n")
        val work = Files.createDirectories(tmp.resolve("wt"))
        Files.writeString(work.resolve(".git"), "gitdir: ${linked.toAbsolutePath()}\n")

        GitBranch.read(work) shouldBe "wt-branch"
    }

    @Test
    fun `a relative gitdir is resolved against the work tree`() {
        val work = Files.createDirectories(tmp.resolve("wt"))
        val git = Files.createDirectories(tmp.resolve("wt/real"))
        Files.writeString(git.resolve("HEAD"), "ref: refs/heads/rel\n")
        Files.writeString(work.resolve(".git"), "gitdir: real\n")

        GitBranch.read(work) shouldBe "rel"
    }

    @Test
    fun `no repository, an empty head and an unknown head give null`() {
        val plain = Files.createDirectories(tmp.resolve("plain"))
        GitBranch.read(plain) shouldBe null
        GitBranch.read(repo(Files.createDirectories(tmp.resolve("empty")), "")) shouldBe null
        GitBranch.read(repo(Files.createDirectories(tmp.resolve("odd")), "something else\n")) shouldBe null
        GitBranch.read(repo(Files.createDirectories(tmp.resolve("blank")), "ref: refs/heads/\n")) shouldBe null
    }

    @Test
    fun `a missing directory gives null`() {
        GitBranch.read(tmp.resolve("missing")) shouldBe null
    }
}
