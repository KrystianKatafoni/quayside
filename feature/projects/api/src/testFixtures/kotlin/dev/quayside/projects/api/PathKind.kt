package dev.quayside.projects.api

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** What a fake sees at a path instead of touching the disk. */
public enum class PathKind {
    DIRECTORY,
    FILE,
    MISSING,
    ;

    public companion object {
        /**
         * Reads the real disk, for running a fake in a contract over a temp directory. Follows
         * symlinks, so a symlink to a directory is [DIRECTORY]; anything that cannot be resolved (a
         * broken symlink, a symlink loop, no permission) is [MISSING], like in [realPathOnDisk].
         */
        public fun onDisk(path: Path): PathKind =
            try {
                if (Files.readAttributes(path, BasicFileAttributes::class.java).isDirectory) DIRECTORY else FILE
            } catch (_: FileSystemException) {
                MISSING
            }

        /**
         * Real path of [path] on disk (`toRealPath()`, symlinks resolved), or null when it cannot be
         * resolved: nothing there, a broken symlink, a symlink loop or no permission. The resolver
         * fakes get in contract tests.
         */
        public fun realPathOnDisk(path: Path): Path? =
            try {
                path.toRealPath()
            } catch (_: FileSystemException) {
                null
            }
    }
}
