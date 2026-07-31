package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.CloudStyle

/**
 * Scene node representing a single cloud.
 *
 * Unlike the sky or sun, a scene may contain
 * multiple CloudNode instances.
 *
 * Future versions will use the position and
 * scale values for animation and procedural
 * cloud generation.
 */
data class CloudNode(

    override val id: String,

    /**
     * Cloud appearance selected by ThemeEngine.
     */
    val style: CloudStyle,

    /**
     * Horizontal position (0.0 - 1.0).
     */
    val x: Float = 0.5f,

    /**
     * Vertical position (0.0 - 1.0).
     */
    val y: Float = 0.2f,

    /**
     * Cloud size multiplier.
     */
    val scale: Float = 1.0f

) : SceneNode
