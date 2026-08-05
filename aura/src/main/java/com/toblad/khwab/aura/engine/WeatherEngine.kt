package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.WeatherState

/**
 * Provides the current weather state for Aura.
 *
 * Weather is supplied by the host application (via
 * [updateWeather]) after querying a live weather provider
 * for the device's location. Until the first update
 * arrives, Aura falls back to a clear-sky default.
 */
class WeatherEngine {

    @Volatile
    private var currentWeather: WeatherState = WeatherState.CLEAR

    /**
     * Returns the current weather.
     */
    fun getCurrentWeather(): WeatherState {
        return currentWeather
    }

    /**
     * Called by the host application whenever fresh,
     * real-world weather data is available.
     */
    fun updateWeather(weather: WeatherState) {
        currentWeather = weather
    }

    /**
     * Refreshes the weather state.
     *
     * Returns the most recently supplied real weather, or
     * the clear-sky default if none has been supplied yet.
     */
    fun refresh(): WeatherState {
        return getCurrentWeather()
    }
}