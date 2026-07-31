package com.toblad.khwab.aura.model

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Represents the logical weather state of the Aura world.
 *
 * Selected by WeatherEngine and used by ThemeEngine
 * to determine the appropriate visual profile.
 * ------------------------------------------------------------------
 */
enum class WeatherState {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    FOG,
    STORM
}