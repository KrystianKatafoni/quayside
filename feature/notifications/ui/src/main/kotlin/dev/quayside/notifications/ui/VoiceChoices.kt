@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.quayside.notifications.ui

import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.TemplateProblem
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import dev.quayside.notifications.api.VoiceSample
import java.util.Locale

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

/** What to tell about Polish voices once the list is loaded: none installed, or exactly one. */
internal sealed interface PolishHint {
    /** No `pl_*` voice is installed. */
    data object None : PolishHint

    /** Exactly one `pl_*` voice, named [name]. */
    data class Only(
        val name: String,
    ) : PolishHint
}

/**
 * The name of [locale] (`pl_PL`, `es_419`) in Polish, e.g. `polski (Polska)`; a code the JDK does not know stays raw.
 */
internal fun languageName(locale: String): String {
    // The pattern admits well-formed codes only, so the builder cannot throw.
    val parsed =
        locale
            .takeIf { LOCALE_CODE.matches(it) }
            ?.split('_')
            ?.let { (language, region) ->
                Locale
                    .Builder()
                    .setLanguage(language)
                    .setRegion(region)
                    .build()
            }
    val known = parsed != null && parsed.getDisplayLanguage(POLISH) != parsed.language
    return if (parsed != null && known && parsed.getDisplayCountry(POLISH) != parsed.country) {
        parsed.getDisplayName(POLISH)
    } else {
        locale
    }
}

private val LOCALE_CODE = Regex("""[a-z]{2,3}_([A-Z]{2}|[0-9]{3})""")
private val POLISH: Locale = Locale.forLanguageTag("pl")
private const val FIXED_SAMPLE = "Tak brzmi głos powiadomień."

/** The hint for [voices]: null while loading (null) or with two or more Polish voices. */
internal fun polishHint(voices: List<VoiceOption>?): PolishHint? {
    val polish = voices?.filter { it.locale.startsWith(prefix = "pl_") } ?: return null
    return when (polish.size) {
        0 -> PolishHint.None
        1 -> PolishHint.Only(polish.single().id.name)
        else -> null
    }
}

/**
 * The sample to play for [draft]: the example of the drafted voice, or a fixed text when there is none or it holds
 * `[[` or `]]`. A rate or volume of the draft that is not valid comes from [saved].
 */
internal fun previewSample(
    draft: SpeechDraft,
    saved: SpeechSettings,
    voices: List<VoiceOption>?,
): VoiceSample {
    val example = voices?.firstOrNull { it.id == draft.voice }?.sample.orEmpty()
    val usable = example.isNotBlank() && SpeechSettings.templateProblem(example) != TemplateProblem.EMBEDDED_COMMAND
    return VoiceSample(
        voice = draft.voice,
        text = if (usable) example else FIXED_SAMPLE,
        rate = parseInRange(draft.rate, SpeechSettings.MIN_RATE..SpeechSettings.MAX_RATE) ?: saved.rate,
        volume = parseInRange(draft.volume, SpeechSettings.MIN_VOLUME..SpeechSettings.MAX_VOLUME) ?: saved.volume,
    )
}
