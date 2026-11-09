package dev.quayside.appearance.impl

import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ContrastAudit
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShapeTokens
import dev.quayside.core.domain.Outcome
import java.util.Locale

/** `#RRGGBB` of an opaque colour, upper case. */
internal fun Rgba.hex(): String = String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue)

/** The docs/08 row of [scheme]: the same derivation as the built-in base schemes. */
private fun CustomScheme.row(): SchemeRow =
    SchemeRow(id.value, name, mode, colors.all().joinToString(" ") { it.hex() }, radius)

/** The theme of [scheme] with derived tokens, a flat opaque shape and no suggested typography. */
internal fun themeOf(scheme: CustomScheme): ThemeSpec =
    ThemeSpec(
        id = scheme.id,
        name = scheme.name,
        mode = scheme.mode,
        colors = deriveTokens(scheme.row()),
        shape = ShapeTokens(scheme.radius, borderWidth = 1, shadow = null, backdrop = Backdrop.OPAQUE),
        defaultTypography = null,
    )

/** The theme of [scheme], or [SchemeError.LowContrast] with every NFR-6 rule its derived tokens break. */
internal fun auditedThemeOf(scheme: CustomScheme): Outcome<ThemeSpec, SchemeError> {
    val spec = themeOf(scheme)
    val problems = ContrastAudit.problems(spec.colors)
    return if (problems.isEmpty()) Outcome.Ok(spec) else Outcome.Err(SchemeError.LowContrast(problems))
}

/**
 * The schemes of [schemes] that may join [base]: the id is free (also among the schemes before it) and the NFR-6 audit
 * passes. Others are dropped, in order, without a message.
 */
internal fun acceptedSchemes(
    base: ThemeCatalog,
    schemes: List<CustomScheme>,
): List<CustomScheme> {
    val taken = base.themes.mapTo(mutableSetOf()) { it.id }
    return schemes.filter { scheme -> auditedThemeOf(scheme) is Outcome.Ok && taken.add(scheme.id) }
}

/** [base] followed by the themes of [accepted] (the result of [acceptedSchemes]). */
internal fun catalogWith(
    base: ThemeCatalog,
    accepted: List<CustomScheme>,
): ThemeCatalog = if (accepted.isEmpty()) base else base.copy(themes = base.themes + accepted.map(::themeOf))
