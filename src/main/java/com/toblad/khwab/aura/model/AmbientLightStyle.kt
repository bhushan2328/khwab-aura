package com.toblad.khwab.aura.model

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Defines ambient lighting presets used to tint
 * the rendered scene.
 *
 * These values are selected by ThemeEngine and
 * interpreted by LightRenderer.
 * ------------------------------------------------------------------
 */
enum class AmbientLightStyle {

    /**
     * Before sunrise.
     */
    PRE_DAWN,

    /**
     * Sunrise lighting.
     */
    SUNRISE,

    /**
     * Morning lighting.
     */
    MORNING,

    /**
     * Bright midday lighting.
     */
    NOON,

    /**
     * Warm afternoon lighting.
     */
    AFTERNOON,

    /**
     * Sunset lighting.
     */
    SUNSET,

    /**
     * Evening lighting.
     */
    EVENING,

    /**
     * Moonlit ambient lighting.
     */
    MOONLIGHT,

    /**
     * Night lighting.
     */
    NIGHT,

    /**
     * Overcast lighting.
     */
    OVERCAST,

    /**
     * Fog-diffused lighting.
     */
    FOG
}
