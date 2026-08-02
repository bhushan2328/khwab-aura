package com.toblad.khwab.aura.model

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Defines the visual appearance of the sky.
 *
 * These values are selected by ThemeEngine and
 * interpreted by SkyRenderer.
 * ------------------------------------------------------------------
 */
enum class SkyStyle {

    /**
     * Before sunrise.
     */
    PRE_DAWN,

    /**
     * Early sunrise.
     */
    DAWN,

    /**
     * Sunrise.
     */
    SUNRISE,

    /**
     * Bright morning sky.
     */
    MORNING,

    /**
     * Midday sky.
     */
    NOON,

    /**
     * Afternoon sky.
     */
    AFTERNOON,

    /**
     * Sunset sky.
     */
    SUNSET,

    /**
     * Twilight / evening sky.
     */
    EVENING,

    /**
     * Night sky.
     */
    NIGHT,

    /**
     * Deep midnight sky.
     */
    MIDNIGHT,

    /**
     * Cloud-covered sky.
     */
    CLOUDY,

    /**
     * Foggy sky.
     */
    FOG,

    /**
     * Stormy sky.
     */
    STORM
}
