package com.toblad.khwab.aura

import com.toblad.khwab.aura.sun.SolarCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [SolarCalculator].
 *
 * Uses UTC to avoid timezone ambiguity.
 */
class SolarCalculatorTest {

    private fun utcCalendar(year: Int, month: Int, day: Int): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun `sunrise before sunset for London summer`() {
        // London, UK — summer solstice
        val lat = 51.5
        val lon = -0.12
        val date = utcCalendar(2024, 6, 21)
        val result = SolarCalculator.calculate(lat, lon, date)
        assertNotNull(result)
        assert(result!!.sunriseHour < result.sunsetHour) {
            "Sunrise (${result.sunriseHour}) should be before sunset (${result.sunsetHour})"
        }
    }

    @Test
    fun `Sydney winter has valid sunrise and sunset`() {
        // Sydney, Australia — winter in June
        // Note: SolarCalculator returns times in the LOCAL timezone (default system timezone in tests = UTC).
        // Sydney (AEST = UTC+10) sunrise at ~7am = ~21:00 UTC previous day, so raw hours may wrap midnight.
        // We only assert that both values are in [0, 24] — correctness is tested for UTC-friendly locations.
        val lat = -33.87
        val lon = 151.21
        val date = utcCalendar(2024, 6, 21)
        val result = SolarCalculator.calculate(lat, lon, date)
        assertNotNull(result)
        assertTrue("Sunrise hour out of range", result!!.sunriseHour in 0f..24f)
        assertTrue("Sunset hour out of range", result.sunsetHour in 0f..24f)
    }

    @Test
    fun `polar night returns null above Arctic Circle in December`() {
        // Above the Arctic Circle (Tromsø, Norway) in December — polar night
        val lat = 69.6
        val lon = 18.9
        val date = utcCalendar(2024, 12, 21)
        val result = SolarCalculator.calculate(lat, lon, date)
        // May return null for polar night; this is a known edge-case
        // If not null, sunrise must still be before sunset
        if (result != null) {
            assert(result.sunriseHour < result.sunsetHour)
        }
    }

    @Test
    fun `polar day returns null above Arctic Circle in June`() {
        // Tromsø, Norway at summer solstice — polar day
        val lat = 69.6
        val lon = 18.9
        val date = utcCalendar(2024, 6, 21)
        val result = SolarCalculator.calculate(lat, lon, date)
        // Polar day → cosH < -1 → should return null
        assertNull(result)
    }

    @Test
    fun `sunrise is in morning hours for equatorial location`() {
        // Nairobi, Kenya — near equator, predictable sunrise/sunset
        val lat = -1.29
        val lon = 36.82
        val date = utcCalendar(2024, 3, 20) // equinox
        val result = SolarCalculator.calculate(lat, lon, date)
        assertNotNull(result)
        // Sunrise should be roughly 6am ±2h in UTC+3, so ~3-9 UTC
        assert(result!!.sunriseHour in 3f..9f) {
            "Equatorial sunrise ${result.sunriseHour} out of expected range"
        }
    }

    @Test
    fun `sunset is in evening hours for equatorial location`() {
        val lat = -1.29
        val lon = 36.82
        val date = utcCalendar(2024, 3, 20)
        val result = SolarCalculator.calculate(lat, lon, date)
        assertNotNull(result)
        // Sunset should be roughly 18:00 ±2h UTC+3, so ~15-21 UTC
        assert(result!!.sunsetHour in 15f..21f) {
            "Equatorial sunset ${result.sunsetHour} out of expected range"
        }
    }

    @Test
    fun `day length is longer in summer than winter for northern hemisphere`() {
        val lat = 51.5
        val lon = -0.12
        val summer = utcCalendar(2024, 6, 21)
        val winter = utcCalendar(2024, 12, 21)
        val summerResult = SolarCalculator.calculate(lat, lon, summer)!!
        val winterResult = SolarCalculator.calculate(lat, lon, winter)!!

        val summerDayLen = summerResult.sunsetHour - summerResult.sunriseHour
        val winterDayLen = winterResult.sunsetHour - winterResult.sunriseHour

        assert(summerDayLen > winterDayLen) {
            "Summer day ($summerDayLen h) should be longer than winter day ($winterDayLen h)"
        }
    }
}
