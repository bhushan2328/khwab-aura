package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.WeatherNode

class WeatherRenderer {

    fun render(
        context: RenderContext
    ): List<WeatherNode> =
        context.scene.nodesOfType<WeatherNode>()
}

