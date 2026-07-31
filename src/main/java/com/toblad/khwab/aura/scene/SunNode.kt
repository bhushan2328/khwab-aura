package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.SunStyle

/**
 * Scene node representing the sun.
 *
 * There is typically one SunNode in a scene.
 * Future versions may include position,
 * size, brightness and animation data.
 */
data class SunNode(

    override val id: String = "sun",

    /**
     * Sun appearance selected by ThemeEngine.
     */
    val style: SunStyle

) : SceneNode

