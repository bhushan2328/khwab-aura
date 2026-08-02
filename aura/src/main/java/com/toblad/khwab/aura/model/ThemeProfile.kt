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
    val animation: AnimationStyle
)