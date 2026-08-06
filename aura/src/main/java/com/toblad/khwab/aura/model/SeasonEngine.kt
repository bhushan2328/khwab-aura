package com.toblad.khwab.aura.season

import com.toblad.khwab.aura.model.Season
import java.util.Calendar

/**
 * Determines the current season from the calendar month and
 * the device's hemisphere (inferred from latitude sign).
 *
 * Falls back to the northern-hemisphere schedule when no
 * latitude is known yet.
 */
object SeasonEngine {

    fun calculate(
        latitude: Double?,
        date: Calendar = Calendar.getInstance()
    ): Season {

        val northernHemisphere = (latitude ?: 0.0) >= 0.0

        val northernSeason = when (date.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Season.WINTER
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Season.SPRING
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Season.SUMMER
            else -> Season.AUTUMN
        }

        return if (northernHemisphere) northernSeason else opposite(northernSeason)
    }

    private fun opposite(season: Season): Season = when (season) {
        Season.WINTER -> Season.SUMMER
        Season.SUMMER -> Season.WINTER
        Season.SPRING -> Season.AUTUMN
        Season.AUTUMN -> Season.SPRING
    }
}