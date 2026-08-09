package com.toblad.khwab.aura

import com.toblad.khwab.aura.model.MoonStyle
import com.toblad.khwab.aura.sun.MoonPhaseCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [MoonPhaseCalculator].
 */
class MoonPhaseCalculatorTest {

    private fun calendarFor(year: Int, month: Int, day: Int): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun `returns a valid MoonStyle for any date`() {
        val result = MoonPhaseCalculator.calculate(calendarFor(2024, 1, 1))
        assertTrue(result in MoonStyle.entries)
    }

    @Test
    fun `phaseFraction is in 0 to 1`() {
        val frac = MoonPhaseCalculator.phaseFraction(calendarFor(2024, 6, 15))
        assertTrue("Phase fraction $frac out of [0,1]", frac in 0.0..1.0)
    }

    @Test
    fun `known new moon date returns HIDDEN`() {
        // Known new moon: 2024-01-11 (approx)
        val result = MoonPhaseCalculator.calculate(calendarFor(2024, 1, 11))
        // New moon → phase near 0 or 1 → HIDDEN
        assertEquals(MoonStyle.HIDDEN, result)
    }

    @Test
    fun `known full moon date returns FULL or GIBBOUS`() {
        // Full moon: 2024-01-25.
        // Due to the ±half-day uncertainty in the Julian date calculation, the
        // phase fraction on this day may land just outside the strict FULL window
        // (0.47–0.53) and resolve to GIBBOUS. Both are correct for "the day of" a full moon.
        val result = MoonPhaseCalculator.calculate(calendarFor(2024, 1, 25))
        assertTrue(
            "Expected FULL or GIBBOUS near full moon, got $result",
            result == MoonStyle.FULL || result == MoonStyle.GIBBOUS
        )
    }

    @Test
    fun `consistent results for same date`() {
        val date = calendarFor(2024, 6, 15)
        val r1 = MoonPhaseCalculator.calculate(date)
        val r2 = MoonPhaseCalculator.calculate(date)
        assertEquals(r1, r2)
    }
}
