package com.toblad.khwab.aura.model

/**
 * Describes the complete visual profile used by Aura.
 *
 * ThemeProfile contains appearance information only.
 * Rendering and animation are handled by other layers.
 */
data class ThemeProfile(

    /**
     * Sky appearance preset.
     */
    val sky: String,

    /**
     * Sun appearance preset.
     */
    val sun: String,

    /**
     * Moon appearance preset.
     */
    val moon: String,

    /**
     * Cloud appearance preset.
     */
    val clouds: String,

    /**
     * Weather effect preset.
     */
    val weatherEffect: String,

    /**
     * Ambient lighting preset.
     */
    val ambientLight: String,

    /**
     * Animation preset.
     */
    val animation: String
)
