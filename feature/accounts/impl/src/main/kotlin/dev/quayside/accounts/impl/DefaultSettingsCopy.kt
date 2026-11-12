package dev.quayside.accounts.impl

import dev.quayside.accounts.api.CopyItem
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/**
 * What a copy from the default account would do: [writes] are the files (and links) to put, relative to the
 * account directory; [overwrites] are those of them that exist with other content. [blocked] is set when one of
 * them would replace a directory, which a backup can not hold.
 */
internal class CopyPlan(
    val writes: List<Path>,
    val overwrites: List<Path>,
    val blocked: Boolean,
    val copied: Set<CopyItem>,
    val skipped: Set<CopyItem>,
)

/**
 * The copy of chosen items from `~/.claude` into an account directory, merging by relative path (ADR 0035 p.5, 7).
 * Never copies anything but the five [CopyItem] paths, so plugins, login and supervisor state stay behind.
 * Links are copied as links. Methods throw [java.io.IOException]; the caller maps it to a stage.
 */
internal object DefaultSettingsCopy {
    private val NAMES =
        mapOf(
            CopyItem.INSTRUCTIONS to "CLAUDE.md",
            CopyItem.AGENTS to "agents",
            CopyItem.SKILLS to "skills",
            CopyItem.COMMANDS to "commands",
            CopyItem.SETTINGS to "settings.json",
        )

    /** Plans the copy of [items] from [from] into [to]; reads both, writes nothing. */
    fun plan(
        from: Path,
        to: Path,
        items: Set<CopyItem>,
    ): CopyPlan {
        val present =
            CopyItem.entries.filter {
                it in items &&
                    Files.exists(from.resolve(NAMES.getValue(it)), NOFOLLOW_LINKS)
            }
        val files = present.flatMap { entries(from, from.resolve(NAMES.getValue(it))) }
        val changed = files.filterNot { identical(from.resolve(it), to.resolve(it)) }
        val overwrites = changed.filter { Files.exists(to.resolve(it), NOFOLLOW_LINKS) }
        return CopyPlan(
            writes = changed,
            overwrites = overwrites,
            blocked = overwrites.any { Files.isDirectory(to.resolve(it), NOFOLLOW_LINKS) },
            copied = present.toSet(),
            skipped = items - present.toSet(),
        )
    }

    /** Puts every file of [plan] into [to]; each one atomically. */
    fun write(
        from: Path,
        to: Path,
        plan: CopyPlan,
    ) {
        plan.writes.forEach { AccountDirectories.place(from.resolve(it), to.resolve(it), ownerOnly = false) }
    }

    private fun entries(
        root: Path,
        item: Path,
    ): List<Path> =
        Files.walk(item).use { walk ->
            walk.filter { !Files.isDirectory(it, NOFOLLOW_LINKS) }.map { root.relativize(it) }.toList()
        }

    private fun identical(
        source: Path,
        dest: Path,
    ): Boolean =
        when {
            Files.isSymbolicLink(source) -> {
                Files.isSymbolicLink(dest) && Files.readSymbolicLink(source) == Files.readSymbolicLink(dest)
            }

            else -> {
                Files.isRegularFile(dest, NOFOLLOW_LINKS) && Files.mismatch(source, dest) == -1L
            }
        }
}
