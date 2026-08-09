package com.toblad.khwab.aura

import com.toblad.khwab.aura.engine.LightingEngine
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.world.TimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LightingEngine].
 */
class LightingEngineTest {

    private val engine = LightingEngine()

    // ── Intensity range ────────────────────────────────────────────────────────

    @Test
    fun `intensity is always between 0 and 1`() {
        for (hour in 0..23) {
            for (weather in WeatherState.entries) {
                val result = engine.update(TimeState(hour, 0, 0), weather)
                assertTrue(
                    "Intensity ${result.intensity} out of range for h=$hour weather=$weather",
                    result.intensity in 0f..1f
                )
            }
        }
    }

    // ── Day vs night ────────────────────────────────────────────────────────────

    @Test
    fun `midday brighter than midnight for clear weather`() {
        val noon = engine.update(TimeState(13, 0, 0), WeatherState.CLEAR)
        val midnight = engine.update(TimeState(0, 0, 0), WeatherState.CLEAR)
        assertTrue(
            "Noon (${noon.intensity}) should be brighter than midnight (${midnight.intensity})",
            noon.intensity > midnight.intensity
        )
    }

    @Test
    fun `night hours capped to low intensity`() {
        val night = engine.update(TimeState(3, 0, 0), WeatherState.CLEAR)
        assertTrue(
            "Night intensity ${night.intensity} should be ≤ 0.30",
            night.intensity <= 0.30f
        )
    }

    // ── Weather effects ────────────────────────────────────────────────────────

    @Test
    fun `storm darker than clear at same time`() {
        val clear = engine.update(TimeState(12, 0, 0), WeatherState.CLEAR)
        val storm = engine.update(TimeState(12, 0, 0), WeatherState.STORM)
        assertTrue(
            "Storm (${storm.intensity}) should be darker than clear (${clear.intensity})",
            storm.intensity < clear.intensity
        )
    }

    @Test
    fun `rain darker than clear at same time`() {
        val clear = engine.update(TimeState(12, 0, 0), WeatherState.CLEAR)
        val rain = engine.update(TimeState(12, 0, 0), WeatherState.RAIN)
        assertTrue(
            "Rain (${rain.intensity}) should be darker than clear (${clear.intensity})",
            rain.intensity < clear.intensity
        )
    }

    @Test
    fun `fog darker than clear at same time`() {
        val clear = engine.update(TimeState(12, 0, 0), WeatherState.CLEAR)
        val fog = engine.update(TimeState(12, 0, 0), WeatherState.FOG)
        assertTrue(
            "Fog (${fog.intensity}) should be darker than clear (${clear.intensity})",
            fog.intensity < clear.intensity
        )
    }

    @Test
    fun `snow approximately same as cloudy`() {
        // Snow multiplier = 0.90, cloudy = 0.80 — snow should be brighter than cloudy
        val snow = engine.update(TimeState(12, 0, 0), WeatherState.SNOW)
        val cloudy = engine.update(TimeState(12, 0, 0), WeatherState.CLOUDY)
        assertTrue(
            "Snow (${snow.intensity}) should be brighter than cloudy (${cloudy.intensity})",
            snow.intensity > cloudy.intensity
        )
    }

    // ── Ambient equals intensity ───────────────────────────────────────────────

    @Test
    fun `ambient equals intensity`() {
        val result = engine.update(TimeState(12, 0, 0), WeatherState.CLEAR)
        assertEquals(result.intensity, result.ambient, 0.001f)
    }
}
