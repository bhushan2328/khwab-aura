package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.AmbientLightStyle

/**
 * Scene node representing ambient lighting.
 *
 * There is typically one LightNode in a scene.
 * Future versions may include light color,
 * intensity, bloom and animated transitions.
 */
data class LightNode(

    override val id: String = "light",

    /**
     * Ambient lighting selected by ThemeEngine.
     */
    val style: AmbientLightStyle,

    /**
     * Light intensity multiplier.
     */
    val intensity: Float = 1.0f

) : SceneNode

