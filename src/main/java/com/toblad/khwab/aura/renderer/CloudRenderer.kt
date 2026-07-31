package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.CloudNode

class CloudRenderer {

    fun render(
        context: RenderContext
    ): List<CloudNode> =
        context.scene.nodesOfType<CloudNode>()
}

