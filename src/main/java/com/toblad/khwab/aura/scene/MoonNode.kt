package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.MoonStyle

/**
 * Scene node representing the moon.
 *
 * There is typically one MoonNode in a scene.
 * Future versions may include phase,
 * position, glow and animation data.
 */
data class MoonNode(

    override val id: String = "moon",

    /**
     * Moon appearance selected by ThemeEngine.
     */
    val style: MoonStyle

) : SceneNode
