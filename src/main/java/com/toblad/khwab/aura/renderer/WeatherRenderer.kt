package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.WeatherNode

/**
 * Responsible for rendering atmospheric
 * weather effects.
 *
 * Receives a WeatherNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will calculate particle
 * systems, rain, snow, fog, lightning,
 * wind and animation data.
 */
class WeatherRenderer {

    /**
     * Processes the supplied WeatherNode.
     */
    fun render(
        node: WeatherNode
    ): WeatherNode {

        return node
    }
}
