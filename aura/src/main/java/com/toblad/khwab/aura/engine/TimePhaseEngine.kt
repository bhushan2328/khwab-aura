package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.world.TimeState

/**
 * Converts the current simulated time into a
 * logical phase of the day.
 *
 * This class is the single source of truth for
 * determining the current TimePhase.
 */
class TimePhaseEngine {

    fun calculate(time: TimeState): TimePhase {

        val minutes = time.hour * 60 + time.minute

        return when (minutes) {
            in 0 until 240 -> TimePhase.MIDNIGHT
            in 240 until 330 -> TimePhase.PRE_DAWN
            in 330 until 390 -> TimePhase.SUNRISE
            in 390 until 690 -> TimePhase.MORNING
            in 690 until 810 -> TimePhase.NOON
            in 810 until 1050 -> TimePhase.AFTERNOON
            in 1050 until 1110 -> TimePhase.SUNSET
            in 1110 until 1260 -> TimePhase.EVENING
            else -> TimePhase.NIGHT
        }
    }
}
