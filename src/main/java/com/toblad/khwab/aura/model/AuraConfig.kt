package com.toblad.khwab.aura.model

/**
 * Global configuration for the Khwab Aura engine.
 *
 * These settings control how Aura behaves.
 * Values may later be loaded from user preferences.
 */
data class AuraConfig(

    /**
     * Enables or disables Aura.
     */
    val enabled: Boolean = false,

    /**
     * Automatically update the theme based on
     * the current time.
     */
    val autoTime: Boolean = true,

    /**
     * Automatically update the theme using
     * live weather.
     */
    val autoWeather: Boolean = true,

    /**
     * Enables background animations.
     */
    val animationsEnabled: Boolean = true,

    /**
     * Enables ambient sounds.
     */
    val ambientSoundEnabled: Boolean = false,

    /**
     * Theme refresh interval in minutes.
     */
    val refreshIntervalMinutes: Int = 60
)
