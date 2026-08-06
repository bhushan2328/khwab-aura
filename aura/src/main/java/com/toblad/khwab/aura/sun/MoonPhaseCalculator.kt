package com.toblad.khwab.aura.sun

import com.toblad.khwab.aura.model.MoonStyle
import java.util.Calendar

/**
 * Computes the real current moon phase, no network required.
 *
 * Uses a known new-moon reference date and the synodic month
 * length (the real ~29.53-day new-moon-to-new-moon cycle).
 */
object MoonPhaseCalculator {

    private const val SYNODIC_MONTH_DAYS = 29.530588853

    // 2000-01-06 18:14 UTC — a known new moon, as a Julian date.
    private const val KNOWN_NEW_MOON_JD = 2451550.1

    fun calculate(date: Calendar = Calendar.getInstance()): MoonStyle {

        val julianDate = date.timeInMillis / 86400000.0 + 2440587.5

        val daysSinceNewMoon = julianDate - KNOWN_NEW_MOON_JD

        val phase =
            (daysSinceNewMoon.mod(SYNODIC_MONTH_DAYS)) / SYNODIC_MONTH_DAYS

        return styleFor(phase)
    }

    /** Returns the raw 0.0–1.0 phase fraction (0 = new, 0.5 = full). */
    fun phaseFraction(date: Calendar = Calendar.getInstance()): Double {

        val julianDate = date.timeInMillis / 86400000.0 + 2440587.5

        val daysSinceNewMoon = julianDate - KNOWN_NEW_MOON_JD

        return (daysSinceNewMoon.mod(SYNODIC_MONTH_DAYS)) / SYNODIC_MONTH_DAYS
    }

    private fun styleFor(phase: Double): MoonStyle {

        return when {
            phase < 0.03 || phase > 0.97 -> MoonStyle.HIDDEN
            phase < 0.22 -> MoonStyle.CRESCENT
            phase < 0.28 -> MoonStyle.HALF
            phase < 0.47 -> MoonStyle.GIBBOUS
            phase < 0.53 -> MoonStyle.FULL
            phase < 0.72 -> MoonStyle.GIBBOUS
            phase < 0.78 -> MoonStyle.HALF
            else -> MoonStyle.CRESCENT
        }
    }
}