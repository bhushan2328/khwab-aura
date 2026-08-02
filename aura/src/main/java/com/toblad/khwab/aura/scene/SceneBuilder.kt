package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.AuraTheme

/**
 * Converts an AuraTheme into a SceneGraph.
 */
class SceneBuilder(

    private val cloudGenerator: CloudGenerator = CloudGenerator()

) {

    /**
     * Builds a complete immutable SceneGraph.
     */
    fun build(
        theme: AuraTheme
    ): SceneGraph {

        val nodes = mutableListOf<SceneNode>()

        // Sky
        nodes += SkyNode(
            style = theme.profile.sky
        )

        // Clouds
        nodes += cloudGenerator.generate(
            theme.profile.clouds
        )

        // Sun
        nodes += SunNode(
            style = theme.profile.sun
        )

        // Moon
        nodes += MoonNode(
            style = theme.profile.moon
        )

        // Weather
        nodes += WeatherNode(
            id = "weather",
            style = theme.profile.weatherEffect
        )

        // Lighting
        nodes += LightNode(
            style = theme.profile.ambientLight
        )

        return SceneGraph(
            nodes = nodes
        )
    }
}