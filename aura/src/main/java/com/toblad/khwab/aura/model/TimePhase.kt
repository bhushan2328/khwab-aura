package com.toblad.khwab.aura.model

/**
 * Represents the current phase of the day.
 *
 * These phases are determined from the actual
 * sun position and local time.
 *
 * The ThemeEngine uses these phases to
 * generate the Aura appearance.
 */
enum class TimePhase {

    /**
     * Before sunrise.
     */
    PRE_DAWN,

    /**
     * Sunrise period.
     */
    SUNRISE,

    /**
     * Morning daylight.
     */
    MORNING,

    /**
     * Midday.
     */
    NOON,

    /**
     * Afternoon.
     */
    AFTERNOON,

    /**
     * Sunset period.
     */
    SUNSET,

    /**
     * Evening after sunset.
     */
    EVENING,

    /**
     * Night.
     */
    NIGHT,

    /**
     * Deep night.
     */
    MIDNIGHT
}

