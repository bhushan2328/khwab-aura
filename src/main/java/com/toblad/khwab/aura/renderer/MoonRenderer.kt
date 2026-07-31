package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.MoonNode

/**
 * Responsible for rendering the moon.
 *
 * Receives a MoonNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will calculate moon
 * phases, glow, orbital position,
 * eclipses and animation.
 */
class MoonRenderer {

    /**
     * Processes the supplied MoonNode.
     */
    fun render(
        node: MoonNode
    ): MoonNode {

        return node
    }
}
