package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.LightNode

/**
 * Responsible for rendering ambient lighting.
 *
 * Receives a LightNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will calculate lighting,
 * bloom, color temperature, shadows,
 * HDR effects and animated transitions.
 */
class LightRenderer {

    /**
     * Processes the supplied LightNode.
     */
    fun render(
        node: LightNode
    ): LightNode {

        return node
    }
}
