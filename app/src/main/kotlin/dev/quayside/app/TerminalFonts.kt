package dev.quayside.app

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.platform.ResourceFont
import dev.quayside.core.designsystem.AmFonts
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.IOException

/**
 * Registers the bundled files of font family [family] (the ones AmFonts gives Compose: `ResourceFont.name` of
 * `AmFonts.family(family)`) with [register], so JediTerm finds the family by name. Returns how many files were
 * registered: 0 for a system family (Menlo, SF Pro), an  unknown one or unreadable files (unreadable ones are
 * logged with a fixed category, never the path; a missing resource is only not counted). Never throws.
 */
internal fun registerTerminalFont(
    family: String,
    logger: Logger,
    register: (Font) -> Boolean = GraphicsEnvironment.getLocalGraphicsEnvironment()::registerFont,
): Int {
    val files = (AmFonts.family(family) as? FontListFontFamily)?.fonts.orEmpty().filterIsInstance<ResourceFont>()
    val count = files.count { file -> loadFont(file.name, logger)?.let(register) == true }
    logger.info("terminal font registered", "files" to count)
    return count
}

private fun loadFont(
    resource: String,
    logger: Logger,
): Font? =
    try {
        AmFonts::class.java.classLoader
            .getResourceAsStream(resource)
            ?.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    } catch (_: java.awt.FontFormatException) {
        logger.warn("terminal font unreadable", "reason" to "format")
        null
    } catch (_: IOException) {
        logger.warn("terminal font unreadable", "reason" to "io")
        null
    }
