package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import java.nio.file.Path

/**
 * Pure input rules shared by every [ProjectsApi] implementation and its fakes (ENG-C5, ENG-C9).
 *
 * No IO. [normalizeDir] is only the syntactic step; resolving symlinks to the real path
 * (`toRealPath()`) is done by the implementation afterwards.
 *
 * Lengths are counted in Unicode code points (`String.codePointCount`), so an emoji outside the
 * Basic Multilingual Plane counts as one, and a name is never cut inside a surrogate pair. Combining
 * sequences and NFC/NFD forms are not merged: `é` written as `e` + accent counts as two.
 */
public object ProjectInputRules {
    /** Longest accepted display name in code points, counted after trimming. */
    public const val MAX_DISPLAY_NAME_LENGTH: Int = 80

    private const val ELLIPSIS = "…"
    private const val ROOT_NAME = "/"
    private const val SPACE = ' '.code
    private const val ZERO_WIDTH_JOINER = 0x200D
    private val INVISIBLE_TYPES =
        setOf(Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SURROGATE)
            .map { it.toInt() }
            .toSet()

    /**
     * Absolute [dir] → its `normalize()`d form (`.` and `..` removed syntactically, symlinks not
     * resolved); relative [dir] → [ProjectError.NotAbsolute] with [dir] as given.
     */
    public fun normalizeDir(dir: Path): Outcome<Path, ProjectError.NotAbsolute> =
        if (dir.isAbsolute) Outcome.Ok(dir.normalize()) else Outcome.Err(ProjectError.NotAbsolute(dir))

    /**
     * Display name for a project in [dir].
     *
     * An explicit [raw] name is trimmed and validated strictly: blank, longer than
     * [MAX_DISPLAY_NAME_LENGTH] code points or with control characters (see
     * [NameProblem.CONTROL_CHARACTERS]) → [ProjectError.InvalidDisplayName].
     *
     * [raw] null → a name derived from [dir] that is always `Ok`: the last segment of [dir] with
     * the same control characters replaced by spaces, trimmed and, when too long, cut to
     * `MAX_DISPLAY_NAME_LENGTH - 1` code points plus `…`. If that is blank (e.g. the root or a
     * whitespace-only segment), the whole path is used the same way, and `/` if that is blank too.
     */
    public fun displayName(
        raw: String?,
        dir: Path,
    ): Outcome<String, ProjectError.InvalidDisplayName> {
        if (raw == null) return Outcome.Ok(fallbackName(dir))
        val name = raw.trim()
        val problem = problemWith(name)
        return if (problem == null) Outcome.Ok(name) else Outcome.Err(ProjectError.InvalidDisplayName(problem))
    }

    private fun fallbackName(dir: Path): String = fallbackName(dir.fileName?.toString(), dir.toString())

    /**
     * The fallback rule on plain strings, so it can be tested with names that a platform path
     * encoding cannot hold (e.g. an emoji under a non-UTF-8 `sun.jnu.encoding`).
     */
    internal fun fallbackName(
        lastSegment: String?,
        fullPath: String,
    ): String =
        sanitized(lastSegment.orEmpty())
            .ifEmpty { sanitized(fullPath) }
            .ifEmpty { ROOT_NAME }

    private fun sanitized(text: String): String {
        val name = text.mapCodePoints { if (isControl(it)) SPACE else it }.trim()
        if (name.codePointLength() <= MAX_DISPLAY_NAME_LENGTH) return name
        val end = name.offsetByCodePoints(0, MAX_DISPLAY_NAME_LENGTH - 1)
        return name.substring(0, end).trimEnd() + ELLIPSIS
    }

    private fun problemWith(name: String): NameProblem? =
        when {
            name.isBlank() -> NameProblem.BLANK
            name.codePointLength() > MAX_DISPLAY_NAME_LENGTH -> NameProblem.TOO_LONG
            name.codePoints().anyMatch(::isControl) -> NameProblem.CONTROL_CHARACTERS
            else -> null
        }

    /**
     * ISO control, format (Cf: bidi overrides, zero-width characters), line and paragraph
     * separators, and surrogates; a code point of a valid surrogate pair is never a surrogate.
     * The zero-width joiner is allowed: multi-person and flag emoji sequences need it.
     */
    private fun isControl(codePoint: Int): Boolean =
        Character.isISOControl(codePoint) ||
            (codePoint != ZERO_WIDTH_JOINER && Character.getType(codePoint) in INVISIBLE_TYPES)

    private fun String.mapCodePoints(transform: (Int) -> Int): String {
        val result = StringBuilder(length)
        codePoints().forEach { result.appendCodePoint(transform(it)) }
        return result.toString()
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)
}
