package com.toblad.khwab.aura.model

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Defines scene-wide animation presets.
 *
 * These values are selected by ThemeEngine and
 * interpreted by AnimationLayer.
 * ------------------------------------------------------------------
 */
enum class AnimationStyle {

    /**
     * No animation.
     */
    NONE,

    /**
     * Very slow movement.
     */
    CALM,

    /**
     * Gentle movement.
     */
    BREEZY,

    /**
     * Normal movement.
     */
    NORMAL,

    /**
     * Strong wind animation.
     */
    WINDY,

    /**
     * Rain animation.
     */
    RAIN,

    /**
     * Snow animation.
     */
    SNOW,

    /**
     * Storm animation.
     */
    STORM
}
