package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.MoonNode

class MoonRenderer {

    fun render(
        context: RenderContext
    ): MoonNode? =
        context.scene.firstNode<MoonNode>()
}

