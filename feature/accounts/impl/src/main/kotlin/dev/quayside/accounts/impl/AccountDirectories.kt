package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.SetupStage
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.map
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

/** Directory-level disk operations of account setup: the new directory, the `projects` link, safe removal. */
internal object AccountDirectories {
    private val OWNER_DIR = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    private val OWNER_FILE = PosixFilePermissions.fromString("rw-------")

    /**
     * Creates the new, owner-only [dir]. Never reuses a directory: an existing one is
     * [AccountSetupError.AlreadyExists] and stays untouched, so a later cleanup removes only what was created here.
     */
    fun create(dir: Path): Outcome<Unit, AccountSetupError> =
        catching({
            if (it is FileAlreadyExistsException) {
                AccountSetupError.AlreadyExists
            } else {
                AccountSetupError.DiskFailed(
                    SetupStage.DIRECTORY,
                )
            }
        }) {
            Files.createDirectories(dir.parent)
            Files.createDirectory(dir, OWNER_DIR)
        }.map { }

    /** Links `<dir>/projects` to [target], which is created when missing (ADR 0035 p.5). */
    fun linkProjects(
        dir: Path,
        target: Path,
    ): Outcome<Unit, AccountSetupError> =
        catching({ AccountSetupError.DiskFailed(SetupStage.LINK) }) {
            Files.createDirectories(target)
            Files.createSymbolicLink(dir.resolve(PROJECTS), target)
        }.map { }

    /**
     * Removes [dir] with everything in it. Links are deleted, never followed, so the `projects` link can not
     * lead to the deletion of the default account's transcripts.
     */
    fun deleteTree(dir: Path): Outcome<Unit, IOException> =
        catching({ it as? IOException ?: IOException(it.javaClass.simpleName) }) {
            Files.walkFileTree(
                dir,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult = FileVisitResult.CONTINUE.also { Files.delete(file) }

                    override fun postVisitDirectory(
                        directory: Path,
                        exc: IOException?,
                    ): FileVisitResult = FileVisitResult.CONTINUE.also { Files.delete(directory) }
                },
            )
        }.map { }

    /**
     * Copies [source] (a file or a link, never followed) to [dest] through a temp file in the same directory and
     * an atomic move; the temp file does not outlive a failure. With [ownerOnly] created directories and the file
     * are private (backups).
     */
    fun place(
        source: Path,
        dest: Path,
        ownerOnly: Boolean,
    ) {
        val parent = dest.parent
        if (ownerOnly) Files.createDirectories(parent, OWNER_DIR) else Files.createDirectories(parent)
        val tmp = parent.resolve("${dest.fileName}.quayside-tmp")
        try {
            Files.copy(source, tmp, NOFOLLOW_LINKS, REPLACE_EXISTING)
            if (ownerOnly && !Files.isSymbolicLink(tmp)) Files.setPosixFilePermissions(tmp, OWNER_FILE)
            Files.move(tmp, dest, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    const val PROJECTS = "projects"
}
