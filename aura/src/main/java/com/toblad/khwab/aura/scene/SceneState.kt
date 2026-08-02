package com.toblad.khwab.aura.scene

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Represents the current dynamic state of the Aura world.
 *
 * Unlike AuraTheme, which describes how Aura should
 * look, SceneState describes how the scene changes
 * over time.
 *
 * SceneState is immutable.
 * ------------------------------------------------------------------
 */
data class SceneState(

    /**
     * Progress through the current day.
     *
     * Range:
     * 0.0 = Start of day
     * 1.0 = End of day
     */
    val dayProgress: Float = 0f,

    /**
     * Horizontal cloud movement.
     */
    val cloudOffset: Float = 0f,

    /**
     * Ambient light intensity.
     */
    val lightIntensity: Float = 1f,

    /**
     * Sun travel progress.
     */
    val sunProgress: Float = 0f,

    /**
     * Moon travel progress.
     */
    val moonProgress: Float = 0f

)
