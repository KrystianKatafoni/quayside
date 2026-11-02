@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.quayside.notifications.ui

import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption

/** One entry of the voice choice: [id] null = system voice; [missing] = saved but not in a loaded list. */
internal data class VoiceChoice(
    val id: VoiceId?,
    val locale: String?,
    val missing: Boolean,
)

/**
 * System voice, then [saved] when it is not in [voices], then [voices] with `pl_*` first, each part by name. While
 * [voices] is null (loading, failure) [saved] has missing = false: we do not know.
 */
internal fun voiceChoices(
    voices: List<VoiceOption>?,
    saved: VoiceId?,
): List<VoiceChoice> {
    val listed = voices.orEmpty().map { VoiceChoice(it.id, it.locale, missing = false) }
    val (polish, rest) = listed.partition { it.locale?.startsWith(prefix = "pl_") == true }
    val kept =
        saved
            ?.takeIf { id -> listed.none { it.id == id } }
            ?.let { VoiceChoice(it, locale = null, missing = voices != null) }
    return listOfNotNull(VoiceChoice(id = null, locale = null, missing = false), kept) + polish + rest
}

/**
 * The index of the first of [names] after [from] (wrapping) that starts with [letter], ignoring case; else null.
 * Only the range A-Z works (no Ł, Ś, Ż).
 */
internal fun jumpTarget(
    names: List<String?>,
    from: Int,
    letter: Char,
): Int? =
    (1..names.size)
        .map { (from + it) % names.size }
        .firstOrNull { names[it]?.startsWith(letter, ignoreCase = true) == true }
