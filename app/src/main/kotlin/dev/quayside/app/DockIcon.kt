package dev.quayside.app

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import java.awt.Image
import java.awt.Taskbar

/**
 * Sets [image] as the Dock icon through `java.awt.Taskbar` (macOS; the window icon alone does not reach the Dock).
 * Returns false and logs a fixed category, never the exception text, when the platform has no Taskbar, the icon
 * feature is missing (headless, other systems) or the security manager refuses; never throws.
 */
internal fun installDockIcon(
    image: Image,
    logger: Logger,
): Boolean =
    try {
        val taskbar = if (Taskbar.isTaskbarSupported()) Taskbar.getTaskbar() else null
        if (taskbar != null && taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage = image
            logger.info("dock icon set")
            true
        } else {
            logger.info("dock icon skipped", "reason" to "unsupported")
            false
        }
    } catch (_: UnsupportedOperationException) {
        logger.warn("dock icon skipped", "reason" to "unsupported")
        false
    } catch (_: SecurityException) {
        logger.warn("dock icon skipped", "reason" to "security")
        false
    }
