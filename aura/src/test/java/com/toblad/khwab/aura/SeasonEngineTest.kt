package com.toblad.khwab.aura

import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.season.SeasonEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [SeasonEngine].
 */
class SeasonEngineTest {

    private fun calendarForMonth(month: Int): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.MONTH, month - 1)
        }
    }

    // ── Northern hemisphere ────────────────────────────────────────────────────

    @Test
    fun `winter in January northern hemisphere`() {
        assertEquals(Season.WINTER, SeasonEngine.calculate(51.5, calendarForMonth(1)))
    }

    @Test
    fun `spring in April northern hemisphere`() {
        assertEquals(Season.SPRING, SeasonEngine.calculate(51.5, calendarForMonth(4)))
    }

    @Test
    fun `summer in July northern hemisphere`() {
        assertEquals(Season.SUMMER, SeasonEngine.calculate(51.5, calendarForMonth(7)))
    }

    @Test
    fun `autumn in October northern hemisphere`() {
        assertEquals(Season.AUTUMN, SeasonEngine.calculate(51.5, calendarForMonth(10)))
    }

    @Test
    fun `winter in December northern hemisphere`() {
        assertEquals(Season.WINTER, SeasonEngine.calculate(51.5, calendarForMonth(12)))
    }

    // ── Southern hemisphere ────────────────────────────────────────────────────

    @Test
    fun `summer in January southern hemisphere`() {
        // Jan is winter in north → summer in south
        assertEquals(Season.SUMMER, SeasonEngine.calculate(-33.87, calendarForMonth(1)))
    }

    @Test
    fun `autumn in April southern hemisphere`() {
        // April is spring in north → autumn in south
        assertEquals(Season.AUTUMN, SeasonEngine.calculate(-33.87, calendarForMonth(4)))
    }

    @Test
    fun `winter in July southern hemisphere`() {
        // July is summer in north → winter in south
        assertEquals(Season.WINTER, SeasonEngine.calculate(-33.87, calendarForMonth(7)))
    }

    @Test
    fun `spring in October southern hemisphere`() {
        // October is autumn in north → spring in south
        assertEquals(Season.SPRING, SeasonEngine.calculate(-33.87, calendarForMonth(10)))
    }

    @Test
    fun `null latitude defaults to northern hemisphere`() {
        // null latitude → assume northern hemisphere
        assertEquals(Season.WINTER, SeasonEngine.calculate(null, calendarForMonth(1)))
        assertEquals(Season.SUMMER, SeasonEngine.calculate(null, calendarForMonth(7)))
    }
}
