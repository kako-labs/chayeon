package dev.chayeon

import dev.chayeon.core.ui.Scene
import dev.chayeon.feature.history.HistoryDestination
import dev.chayeon.feature.meet.MeetDestination
import dev.chayeon.feature.signature.SignatureDestination

internal fun appScene(route: String?, homeProtected: Boolean): Scene = when {
    route == MeetDestination.HOME && homeProtected -> Scene.HOME
    route == HistoryDestination.HOME_ZONE -> Scene.HOME
    route == MeetDestination.DRIVING || route == MeetDestination.ENDED -> Scene.ROAD
    route in setOf(HistoryDestination.FIRST_RUN, HistoryDestination.PROFILE_SETUP, HistoryDestination.CONSENT,
        HistoryDestination.RELATIONSHIPS, HistoryDestination.PERSON, HistoryDestination.AFTER_REQUEST) -> Scene.DAWN
    route?.startsWith("signature/") == true || route == HistoryDestination.PROFILE_EDIT -> Scene.PLAIN
    else -> Scene.PARKING
}

internal fun hasDetailChrome(route: String?): Boolean = route in setOf(
    MeetDestination.CANDIDATE, MeetDestination.REQUESTS,
    HistoryDestination.PROFILE_EDIT, HistoryDestination.HOME_ZONE, HistoryDestination.PERSON,
    HistoryDestination.AFTER_REQUEST, HistoryDestination.SAFETY, HistoryDestination.BLOCKS, HistoryDestination.CLEANUP,
    SignatureDestination.MY_SIGNATURE, SignatureDestination.TRAIT, SignatureDestination.VISIBILITY,
    SignatureDestination.RECAP, SignatureDestination.DRIVE_SETTINGS,
)
