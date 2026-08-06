package com.toblad.khwab.aura.model

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Describes the complete visual profile used by Aura.
 *
 * ThemeProfile contains appearance information only.
 * Rendering, animation, and simulation are handled
 * by higher layers.
 * ------------------------------------------------------------------
 */
data class ThemeProfile(

    /**
     * Sky appearance preset.
     */
    val sky: SkyStyle,

    /**
     * Sun appearance preset.
     */
    val sun: SunStyle,

    /**
     * Moon appearance preset.
     */
    val moon: MoonStyle,

    /**
     * Cloud appearance preset.
     */
    val clouds: CloudStyle,

    /**
     * Weather effect preset.
     */
    val weatherEffect: WeatherEffectStyle,

    /**
     * Ambient lighting preset.
     */
    val ambientLight: AmbientLightStyle,

    /**
     * Animation preset.
     */
    val animation: AnimationStyle,

    /**
     * Current season, used for seasonal particle ambience
     * (falling leaves, petals, fireflies).
     */
    val season: Season = Season.SUMMER,

    /**
     * Real-world storm severity, normalized 0.0–1.0, derived
     * from live wind speed and precipitation. Scales how
     * intense rain/lightning visuals appear.
     */
    val stormIntensity: Float = 0.5f
)