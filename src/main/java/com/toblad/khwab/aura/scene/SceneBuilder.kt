package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.AuraTheme

/**
 * Converts an AuraTheme into a SceneGraph.
 *
 * This class is responsible for translating
 * high-level theme information into
 * renderable scene nodes.
 */
class SceneBuilder {

    /**
     * Builds a complete SceneGraph from the
     * supplied AuraTheme.
     */
    fun build(
        theme: AuraTheme
    ): SceneGraph {

        val nodes = mutableListOf<SceneNode>()

        nodes += SkyNode(
            style = theme.profile.sky
        )

        nodes += CloudNode(
            id = "cloud-1",
            style = theme.profile.clouds
        )

        nodes += SunNode(
            style = theme.profile.sun
        )

        nodes += MoonNode(
            style = theme.profile.moon
        )

        nodes += WeatherNode(
            id = "weather",
            style = theme.profile.weatherEffect
        )

        nodes += LightNode(
            style = theme.profile.ambientLight
        )

        return SceneGraph(
            nodes = nodes
        )
    }
}
