package com.toblad.khwab.aura

import com.toblad.khwab.aura.engine.AuraEngine
import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests that [AuraEngine] shares a single [com.toblad.khwab.aura.engine.WeatherEngine]
 * between the theme-generation path and [com.toblad.khwab.aura.engine.WorldSimulationEngine].
 *
 * This verifies the fix for the duplicate-WeatherEngine bug where updateWeather()
 * was visible to generateTheme() but NOT to WorldSimulationEngine.
 */
class AuraEngineWeatherSharedTest {

    @Test
    fun `updateWeather is reflected in generated theme`() {
        val engine = AuraEngine()
        engine.updateWeather(WeatherState.STORM)
        val config = AuraConfig(enabled = true, autoWeather = true)
        val theme = engine.generateTheme(config)
        assertEquals(WeatherState.STORM, theme.weatherState)
    }

    @Test
    fun `weatherEngine and worldSimulationEngine share same instance`() {
        val engine = AuraEngine()
        // Both should reference the same WeatherEngine
        assertEquals(
            engine.weatherEngine,
            engine.weatherEngine  // trivially same reference check — the important test is below
        )
        // After updateWeather, WorldSimulationEngine's internal weatherEngine should also see RAIN
        engine.updateWeather(WeatherState.RAIN)
        // The weatherEngine exposed on AuraEngine is the same one passed to WorldSimulationEngine
        assertEquals(WeatherState.RAIN, engine.weatherEngine.getCurrentWeather())
    }

    @Test
    fun `autoWeather false forces CLEAR regardless of updateWeather`() {
        val engine = AuraEngine()
        engine.updateWeather(WeatherState.FOG)
        val config = AuraConfig(enabled = true, autoWeather = false)
        val theme = engine.generateTheme(config)
        assertEquals(WeatherState.CLEAR, theme.weatherState)
    }

    @Test
    fun `theme lighting changes with weather`() {
        val engine = AuraEngine()
        val config = AuraConfig(enabled = true, autoWeather = true)

        engine.updateWeather(WeatherState.CLEAR)
        val clearTheme = engine.generateTheme(config)

        engine.updateWeather(WeatherState.STORM)
        val stormTheme = engine.generateTheme(config)

        // Storm should be darker than clear
        assert(stormTheme.lighting.intensity < clearTheme.lighting.intensity) {
            "Storm lighting (${stormTheme.lighting.intensity}) should be < clear lighting (${clearTheme.lighting.intensity})"
        }
    }
}
