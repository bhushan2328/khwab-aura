package com.toblad.khwab.aura

import com.toblad.khwab.aura.engine.WeatherEngine
import com.toblad.khwab.aura.model.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WeatherEngine].
 */
class WeatherEngineTest {

    @Test
    fun `default weather is CLEAR`() {
        val engine = WeatherEngine()
        assertEquals(WeatherState.CLEAR, engine.getCurrentWeather())
    }

    @Test
    fun `updateWeather reflects in getCurrentWeather`() {
        val engine = WeatherEngine()
        engine.updateWeather(WeatherState.STORM)
        assertEquals(WeatherState.STORM, engine.getCurrentWeather())
    }

    @Test
    fun `refresh returns current weather`() {
        val engine = WeatherEngine()
        engine.updateWeather(WeatherState.RAIN)
        assertEquals(WeatherState.RAIN, engine.refresh())
    }

    @Test
    fun `weather can be updated multiple times`() {
        val engine = WeatherEngine()
        engine.updateWeather(WeatherState.RAIN)
        engine.updateWeather(WeatherState.SNOW)
        engine.updateWeather(WeatherState.FOG)
        assertEquals(WeatherState.FOG, engine.getCurrentWeather())
    }

    @Test
    fun `all weather states can be set`() {
        val engine = WeatherEngine()
        for (state in WeatherState.entries) {
            engine.updateWeather(state)
            assertEquals(state, engine.getCurrentWeather())
        }
    }
}
