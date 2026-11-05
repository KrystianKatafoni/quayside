package dev.quayside.core.designsystem

// A circle of radius r as path commands (two arcs), because the path parser has no <circle>.
private const val RING_9 = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0"
private const val DOT_2 = "a2 2 0 1 0 4 0a2 2 0 1 0-4 0"

// A 18x16 frame with 2 corners at (3, 4), and a 12x12 square with 2 corners at (6, 6), as path commands.
private const val FRAME = "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
private const val SQUARE = "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"

/**
 * Line icons: 24x24 grid, 1.8 stroke, round caps and joins, no fill. Geometry from the Quayside mockups of
 * 2026-11-04; STOPPED, REMOVE, MORE, BACK drawn in this repo. Status icons differ in shape (NFR-6).
 *
 * [pathData] is SVG path data on the 24 grid.
 */
public enum class IconKind(
    internal val pathData: String,
) {
    FOLDER("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"),
    WAITING("$RING_9 M12 7v5l3 2"),
    WORKING("M3 12h4l3-8 4 16 3-8h4"),
    DONE("$RING_9 M8 12.5l3 3 5-6"),
    FAILED("$RING_9 M9 9l6 6M15 9l-6 6"),
    PERMISSION("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z M12 9v4M12 16v.01"),
    STOPPED("$RING_9 M10 9h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z"),
    ATTACH("$FRAME M7 9l3 3-3 3M13 15h4"),
    REPLY("M4 5h16v11H9l-5 4z"),
    RESPAWN("M20 11a8 8 0 1 0-2 5.5M20 5v6h-6"),
    STOP(SQUARE),
    REMOVE("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"),
    SEARCH("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0 M16 16l4 4"),
    NOTIFICATIONS("M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z M10 20a2 2 0 0 0 4 0"),
    VOICE("M4 10v4h4l5 4V6l-5 4zM16 9a4 4 0 0 1 0 6"),
    BRANCH("M4 6$DOT_2 M4 18$DOT_2 M16 9$DOT_2 M6 8v8M18 11c0 4-6 3-12 5"),
    SPLIT("$FRAME M12 4v16"),
    MAXIMIZE("M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"),
    PLUS("M12 5v14M5 12h14"),
    CLOSE("M6 6l12 12M18 6L6 18"),
    MORE("M6 12v.01M12 12v.01M18 12v.01"),
    BACK("M19 12H5M11 6l-6 6 6 6"),
}
