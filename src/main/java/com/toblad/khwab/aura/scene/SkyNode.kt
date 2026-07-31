package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.SkyStyle

/**
 * Scene node representing the sky.
 *
 * There is typically one SkyNode in a scene.
 * It describes how the background sky should
 * be rendered.
 */
data class SkyNode(

    override val id: String = "sky",

    /**
     * Sky appearance selected by ThemeEngine.
     */
    val style: SkyStyle

) : SceneNode

