package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.world.TimeState

/**
 * Converts the current time into a logical phase of the day.
 *
 * When real sunrise/sunset hours are supplied (computed from
 * the device's actual location via SolarCalculator), phases
 * are derived from those real solar times. Otherwise this
 * falls back to a fixed, approximate schedule.
 *
 * This class is the single source of truth for
 * determining the current TimePhase.
 */
class TimePhaseEngine {

    fun calculate(
        time: TimeState,
        sunriseHour: Float? = null,
        sunsetHour: Float? = null
    ): TimePhase {

        if (sunriseHour == null || sunsetHour == null || sunsetHour <= sunriseHour) {
            return calculateFixed(time.hour * 60 + time.minute)
        }

        return calculateSolar(
            minutesOfDay = time.hour * 60 + time.minute,
            sunrise = (sunriseHour * 60).toInt(),
            sunset = (sunsetHour * 60).toInt()
        )
    }

    private fun calculateFixed(minutes: Int): TimePhase {

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

    /**
     * Derives the phase from real sunrise/sunset, expressed
     * as minutes-since-sunrise so the schedule works correctly
     * across the midnight wraparound. Boundaries are visited
     * in a single ascending pass from the sunrise instant
     * (0) all the way back around to just before the next
     * sunrise (1440).
     */
    private fun calculateSolar(
        minutesOfDay: Int,
        sunrise: Int,
        sunset: Int
    ): TimePhase {

        val dayLength = ((sunset - sunrise) % 1440 + 1440) % 1440
        val nightLength = 1440 - dayLength

        val sinceSunrise = ((minutesOfDay - sunrise) % 1440 + 1440) % 1440

        val halfTwilight = 20
        val preDawnSpan = 90
        val eveningSpan = 120

        val solarNoonOffset = dayLength / 2
        val nightMidpoint = dayLength + nightLength / 2
        val midnightHalfWidth = nightLength / 6

        return when {
            sinceSunrise < halfTwilight -> TimePhase.SUNRISE
            sinceSunrise < solarNoonOffset - halfTwilight -> TimePhase.MORNING
            sinceSunrise < solarNoonOffset + halfTwilight -> TimePhase.NOON
            sinceSunrise < dayLength - halfTwilight -> TimePhase.AFTERNOON
            sinceSunrise < dayLength + halfTwilight -> TimePhase.SUNSET
            sinceSunrise < dayLength + halfTwilight + eveningSpan -> TimePhase.EVENING
            sinceSunrise < nightMidpoint - midnightHalfWidth -> TimePhase.NIGHT
            sinceSunrise < nightMidpoint + midnightHalfWidth -> TimePhase.MIDNIGHT
            sinceSunrise < 1440 - preDawnSpan -> TimePhase.NIGHT
            sinceSunrise < 1440 - halfTwilight -> TimePhase.PRE_DAWN
            else -> TimePhase.SUNRISE
        }
    }
}