package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SkyNode

/**
 * Responsible for rendering the sky layer.
 *
 * Receives a SkyNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will generate gradients,
 * atmospheric scattering, stars and other
 * sky rendering data.
 */
class SkyRenderer {

    /**
     * Processes the supplied SkyNode.
     */
    fun render(
        node: SkyNode
    ): SkyNode {

        return node
    }
}
