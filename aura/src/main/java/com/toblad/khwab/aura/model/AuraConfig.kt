package com.toblad.khwab.aura.model

import com.toblad.khwab.aura.AuraConstants

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
     * Enables ambient sounds. Defaults to on — flip to false
     * once a settings screen exists to let users mute it.
     */
    val ambientSoundEnabled: Boolean = true,

    /**
     * Theme refresh interval in minutes.
     */
    val refreshIntervalMinutes: Int = AuraConstants.DEFAULT_REFRESH_INTERVAL_MINUTES,

    /**
     * Device's current latitude, used to compute real
     * sunrise/sunset, moon, and season. Null until the host
     * app supplies a location.
     */
    val latitude: Double? = null,

    /**
     * Device's current longitude, used to compute real
     * sunrise/sunset. Null until the host app supplies a
     * location.
     */
    val longitude: Double? = null,

    /**
     * Real-world storm severity, normalized 0.0–1.0, supplied
     * by the host app from live wind speed / precipitation.
     */
    val stormIntensity: Float = 0.5f
)