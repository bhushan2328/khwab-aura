package com.toblad.khwab.aura.model

/**
 * Describes the visual environment that Aura
 * should render.
 *
 * This class contains appearance information
 * only. Rendering is handled elsewhere.
 */
data class ThemeProfile(

    /**
     * Sky preset.
     */
    val sky: String,

    /**
     * Sun or moon appearance.
     */
    val celestialBody: String,

    /**
     * Cloud preset.
     */
    val clouds: String,

    /**
     * Weather effect.
     */
    val weatherEffect: String,

    /**
     * Ambient lighting preset.
     */
    val ambientLight: String
)
