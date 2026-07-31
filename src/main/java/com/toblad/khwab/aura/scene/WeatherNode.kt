package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.WeatherEffectStyle

/**
 * Scene node representing a weather effect.
 *
 * Multiple WeatherNode instances may exist
 * in a scene to combine effects such as
 * rain, lightning and fog.
 */
data class WeatherNode(

    override val id: String,

    /**
     * Weather effect to render.
     */
    val style: WeatherEffectStyle,

    /**
     * Relative intensity (0.0 - 1.0).
     */
    val intensity: Float = 1.0f

) : SceneNode

