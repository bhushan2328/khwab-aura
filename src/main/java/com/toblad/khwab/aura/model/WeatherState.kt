package com.toblad.khwab.aura.model

/**
 * Represents the current real-world weather.
 *
 * The WeatherEngine determines the active
 * weather state using live weather data.
 */
enum class WeatherState {

    /**
     * Clear sky.
     */
    CLEAR,

    /**
     * Mostly clear with a few clouds.
     */
    PARTLY_CLOUDY,

    /**
     * Cloudy conditions.
     */
    CLOUDY,

    /**
     * Light rain.
     */
    LIGHT_RAIN,

    /**
     * Moderate rain.
     */
    RAIN,

    /**
     * Heavy rainfall.
     */
    HEAVY_RAIN,

    /**
     * Thunderstorm with lightning.
     */
    THUNDERSTORM,

    /**
     * Snowfall.
     */
    SNOW,

    /**
     * Fog or mist.
     */
    FOG,

    /**
     * Windy weather.
     */
    WINDY
}
