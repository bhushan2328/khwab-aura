package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SunNode

class SunRenderer {

    fun render(
        context: RenderContext
    ): SunNode? =
        context.scene.firstNode<SunNode>()
}

