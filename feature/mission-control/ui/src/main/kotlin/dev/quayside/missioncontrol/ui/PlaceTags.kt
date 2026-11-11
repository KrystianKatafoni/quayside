package dev.quayside.missioncontrol.ui

/** Test tags of the list of places (AM-314); the constants are in [MissionControlTags]. */
internal object PlaceTags {
    /** A choice [id] (a [PlaceChoice.id], never a subfolder name) in the open list of places. */
    fun choice(id: String): String = "missionControl.place.choice.$id"
}
