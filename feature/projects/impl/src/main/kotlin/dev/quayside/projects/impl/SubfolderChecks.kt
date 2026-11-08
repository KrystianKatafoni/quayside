package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.getOrElse
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.projects.api.ProjectInputRules
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.SubfolderProblem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Disk checks of [dev.quayside.projects.api.ProjectsApi.setSubfolders], run on the io dispatcher outside the lock. */
internal object SubfolderChecks {
    /**
     * Checks every name of [names] in the order given: the pure rules first (`ProjectInputRules.subfolderName`),
     * then the disk: a real directory directly under [Project.dir], not a symlink. The first failing name gives the
     * `InvalidSubfolder`; more than `MAX_SUBFOLDERS` distinct names give `TOO_MANY`. On success returns the names
     * as the disk lists them (the case of the directory name, which matters on a case-insensitive file system),
     * without names that denote the same directory, in order of first occurrence.
     */
    suspend fun resolve(
        project: Project,
        names: List<SubfolderName>,
        io: CoroutineDispatcher,
    ): Outcome<List<SubfolderName>, ProjectError> =
        withContext(io) {
            val children = childNames(project.dir)
            val resolved = mutableListOf<SubfolderName>()
            for (name in names) {
                when (val checked = check(project, name, children)) {
                    is Outcome.Err -> return@withContext checked
                    is Outcome.Ok -> resolved += checked.value
                }
            }
            val distinct = resolved.distinct()
            if (distinct.size > ProjectInputRules.MAX_SUBFOLDERS) {
                tooMany(distinct[ProjectInputRules.MAX_SUBFOLDERS])
            } else {
                Outcome.Ok(distinct)
            }
        }

    /**
     * Direct subdirectories of the real directory [real] that can be subfolders: not hidden, not symlinks,
     * readable; sorted, at most `MAX_SUBFOLDERS`. Runs on [io]; an unreadable [real] gives an empty list.
     */
    suspend fun list(
        real: Path,
        io: CoroutineDispatcher,
    ): List<SubfolderName> =
        withContext(io) {
            childNames(real)
                .filter { !it.startsWith(".") && isListable(real.resolve(it)) }
                .map(::SubfolderName)
                .sortedWith(ProjectGroups.order)
                .take(ProjectInputRules.MAX_SUBFOLDERS)
        }

    private fun check(
        project: Project,
        name: SubfolderName,
        children: List<String>,
    ): Outcome<SubfolderName, ProjectError> =
        ProjectInputRules
            .subfolderName(name.value)
            // The spelling of the disk (e.g. NFD on APFS) must pass the rules too, or the saved file would be broken.
            .flatMap { spelledOnDisk(name, diskName(project.dir, name.value, children)) }
            .flatMap { real ->
                val problem = problemOf(ProjectGroups.dirOf(project, real))
                if (problem == null) Outcome.Ok(real) else Outcome.Err(ProjectError.InvalidSubfolder(name, problem))
            }

    /** [disk] must pass the rules; the error names the [asked] name, as for every other failure. */
    private fun spelledOnDisk(
        asked: SubfolderName,
        disk: String,
    ): Outcome<SubfolderName, ProjectError> =
        when (val ruled = ProjectInputRules.subfolderName(disk)) {
            is Outcome.Ok -> {
                ruled
            }

            is Outcome.Err -> {
                Outcome.Err(ProjectError.InvalidSubfolder(asked, (ruled.error as ProjectError.InvalidSubfolder).reason))
            }
        }

    private fun tooMany(name: SubfolderName): Outcome<List<SubfolderName>, ProjectError> =
        Outcome.Err(ProjectError.InvalidSubfolder(name, SubfolderProblem.TOO_MANY))

    /** The spelling the disk lists for [name]: the exact one, else the child that is the same directory. */
    private fun diskName(
        dir: Path,
        name: String,
        children: List<String>,
    ): String =
        if (name in children) {
            name
        } else {
            children.firstOrNull {
                !Files.isSymbolicLink(dir.resolve(it)) &&
                    sameFile(dir.resolve(it), dir.resolve(name))
            }
                ?: name
        }

    private fun sameFile(
        a: Path,
        b: Path,
    ): Boolean = catching<Boolean, Unit>({}) { Files.isSameFile(a, b) }.getOrElse { false }

    private fun problemOf(path: Path): SubfolderProblem? {
        val attributes =
            catching<BasicFileAttributes, Unit>({}) {
                Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }.getOrElse { return SubfolderProblem.MISSING }
        return when {
            attributes.isSymbolicLink -> SubfolderProblem.SYMLINK
            attributes.isDirectory -> null
            else -> SubfolderProblem.NOT_A_DIRECTORY
        }
    }

    private fun isListable(path: Path): Boolean =
        Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && Files.isReadable(path)

    private fun childNames(dir: Path): List<String> =
        catching<List<String>, Unit>({}) {
            Files.newDirectoryStream(dir).use { stream -> stream.map { it.fileName.toString() } }
        }.getOrElse { emptyList() }
}
