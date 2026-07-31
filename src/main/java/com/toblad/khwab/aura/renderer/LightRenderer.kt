package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.LightNode

class LightRenderer {

    fun render(
        context: RenderContext
    ): LightNode? =
        context.scene.firstNode<LightNode>()
}

