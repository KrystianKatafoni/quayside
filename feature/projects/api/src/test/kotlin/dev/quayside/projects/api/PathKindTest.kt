package dev.quayside.projects.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PathKindTest {
    @Test
    fun `onDisk follows symlinks and treats a broken one as missing`(
        @TempDir tempDir: Path,
    ) {
        val dir = Files.createDirectory(tempDir.resolve("dir"))
        val file = Files.writeString(tempDir.resolve("file.txt"), "content")

        PathKind.onDisk(dir) shouldBe PathKind.DIRECTORY
        PathKind.onDisk(file) shouldBe PathKind.FILE
        PathKind.onDisk(Files.createSymbolicLink(tempDir.resolve("dir-link"), dir)) shouldBe PathKind.DIRECTORY
        PathKind.onDisk(Files.createSymbolicLink(tempDir.resolve("file-link"), file)) shouldBe PathKind.FILE
        PathKind.onDisk(Files.createSymbolicLink(tempDir.resolve("broken"), tempDir.resolve("x"))) shouldBe
            PathKind.MISSING
        PathKind.onDisk(tempDir.resolve("missing")) shouldBe PathKind.MISSING
    }

    @Test
    fun `realPathOnDisk resolves symlinks and returns null when nothing is there`(
        @TempDir tempDir: Path,
    ) {
        val dir = Files.createDirectory(tempDir.resolve("dir"))
        val link = Files.createSymbolicLink(tempDir.resolve("link"), dir)

        PathKind.realPathOnDisk(link) shouldBe dir.toRealPath()
        PathKind.realPathOnDisk(Files.createSymbolicLink(tempDir.resolve("broken"), tempDir.resolve("x"))) shouldBe null
        PathKind.realPathOnDisk(tempDir.resolve("missing")) shouldBe null
    }

    @Test
    fun `a symlink loop is missing and has no real path`(
        @TempDir tempDir: Path,
    ) {
        val a = tempDir.resolve("a")
        Files.createSymbolicLink(a, tempDir.resolve("b"))
        Files.createSymbolicLink(tempDir.resolve("b"), a)

        PathKind.onDisk(a) shouldBe PathKind.MISSING
        PathKind.realPathOnDisk(a) shouldBe null
        PathKind.realPathOnDisk(a.resolve("child")) shouldBe null
    }
}
