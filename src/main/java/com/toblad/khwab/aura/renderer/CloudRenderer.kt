package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.CloudNode

/**
 * Responsible for rendering cloud layers.
 *
 * Receives a CloudNode from the SceneGraph
 * and returns it unchanged for now.
 *
 * Future versions will calculate cloud
 * movement, density, scale and animation.
 */
class CloudRenderer {

    /**
     * Processes the supplied CloudNode.
     */
    fun render(
        node: CloudNode
    ): CloudNode {

        return node
    }
}
