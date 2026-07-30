package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.TimePhase
import java.time.LocalTime

/**
 * Determines the current phase of the day.
 *
 * Milestone 2 still uses the local device time.
 * Future versions will integrate with SunEngine
 * for true sunrise and sunset calculations.
 */
class TimeEngine {

    /**
     * Returns the current phase using the
     * device's local time.
     */
    fun getCurrentPhase(): TimePhase {
        return getPhase(LocalTime.now())
    }

    /**
     * Returns the phase for the supplied time.
     * Useful for testing and future integrations.
     */
    fun getPhase(time: LocalTime): TimePhase {

        val hour = time.hour

        return when (hour) {
            in 0..3 -> TimePhase.MIDNIGHT
            in 4..5 -> TimePhase.PRE_DAWN
            in 6..7 -> TimePhase.SUNRISE
            in 8..11 -> TimePhase.MORNING
            12 -> TimePhase.NOON
            in 13..16 -> TimePhase.AFTERNOON
            in 17..18 -> TimePhase.SUNSET
            in 19..21 -> TimePhase.EVENING
            else -> TimePhase.NIGHT
        }
    }
}
