package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.WeatherState

/**
 * Provides the current weather state for Aura.
 *
 * Future versions will retrieve weather from a
 * provider and map it to WeatherState.
 */
class WeatherEngine {

    /**
     * Returns the current weather.
     */
    fun getCurrentWeather(): WeatherState {
        return WeatherState.CLEAR
    }

    /**
     * Refreshes the weather state.
     *
     * Placeholder implementation for Milestone 2.
     */
    fun refresh(): WeatherState {
        return getCurrentWeather()
    }
}

