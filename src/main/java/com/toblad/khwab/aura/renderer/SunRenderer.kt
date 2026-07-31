package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SunNode

/**
 * Responsible for rendering the sun.
 *
 * Receives a SunNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will calculate
 * position, glow, brightness,
 * atmospheric scattering and animation.
 */
class SunRenderer {

    /**
     * Processes the supplied SunNode.
     */
    fun render(
        node: SunNode
    ): SunNode {

        return node
    }
}
