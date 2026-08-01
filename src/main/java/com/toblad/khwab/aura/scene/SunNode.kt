package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.SunStyle

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Scene node representing the sun.
 *
 * This node contains immutable scene data used
 * by the renderer to draw the sun.
 *
 * Future versions may extend this node with
 * rotation, corona, flare and eclipse data.
 * ------------------------------------------------------------------
 */
data class SunNode(

    override val id: String = "sun",

    /**
     * Sun appearance selected by ThemeEngine.
     */
    val style: SunStyle,

    /**
     * Horizontal position (0.0 - 1.0).
     */
    val x: Float = 0.80f,

    /**
     * Vertical position (0.0 - 1.0).
     */
    val y: Float = 0.22f,

    /**
     * Relative radius multiplier.
     */
    val radius: Float = 1.0f,

    /**
     * Brightness multiplier.
     */
    val brightness: Float = 1.0f,

    /**
     * Glow intensity.
     */
    val glow: Float = 1.0f

) : SceneNode
