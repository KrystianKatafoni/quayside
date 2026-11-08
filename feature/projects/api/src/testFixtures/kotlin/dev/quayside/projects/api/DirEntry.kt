package dev.quayside.projects.api

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * One child of a directory as [FakeProjectInspector] sees it, instead of touching the disk.
 *
 * @property name file name of the child.
 * @property isDirectory a real directory (a symlink is described by [isSymlink], whatever it points to).
 * @property isSymlink the child is a symbolic link.
 */
public data class DirEntry(
    val name: String,
    val isDirectory: Boolean = true,
    val isSymlink: Boolean = false,
) {
    public companion object {
        /** Reads the children of [dir] from the real disk, for running a fake in a contract over a temp directory. */
        public fun onDisk(dir: Path): List<DirEntry> =
            Files.newDirectoryStream(dir).use { children ->
                children.map {
                    DirEntry(
                        name = it.fileName.toString(),
                        isDirectory = Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS),
                        isSymlink = Files.isSymbolicLink(it),
                    )
                }
            }
    }
}
