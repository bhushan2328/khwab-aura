package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.MoonStyle

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Scene node representing the moon.
 *
 * Contains immutable rendering data.
 *
 * Future versions may extend this node with
 * moon phases, eclipse data and surface
 * texture information.
 * ------------------------------------------------------------------
 */
data class MoonNode(

    override val id: String = "moon",

    /**
     * Moon appearance selected by ThemeEngine.
     */
    val style: MoonStyle,

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
