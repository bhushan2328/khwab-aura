package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SkyNode

class SkyRenderer {

    fun render(
        context: RenderContext
    ): SkyNode? =
        context.scene.firstNode<SkyNode>()
}

