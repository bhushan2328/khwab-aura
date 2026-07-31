package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.CloudNode
import com.toblad.khwab.aura.scene.LightNode
import com.toblad.khwab.aura.scene.MoonNode
import com.toblad.khwab.aura.scene.SceneGraph
import com.toblad.khwab.aura.scene.SkyNode
import com.toblad.khwab.aura.scene.SunNode
import com.toblad.khwab.aura.scene.WeatherNode

/**
 * Central rendering coordinator.
 *
 * Receives a SceneGraph and delegates each
 * SceneNode to its dedicated renderer.
 */
class AuraRenderer(

    private val skyRenderer: SkyRenderer = SkyRenderer(),
    private val cloudRenderer: CloudRenderer = CloudRenderer(),
    private val sunRenderer: SunRenderer = SunRenderer(),
    private val moonRenderer: MoonRenderer = MoonRenderer(),
    private val weatherRenderer: WeatherRenderer = WeatherRenderer(),
    private val lightRenderer: LightRenderer = LightRenderer(),
    private val animationController: AnimationController = AnimationController()
) {

    /**
     * Renders the complete SceneGraph.
     */
    fun render(
        scene: SceneGraph
    ) {

        scene.firstNode<SkyNode>()?.let {
            skyRenderer.render(it)
        }

        scene.nodesOfType<CloudNode>().forEach {
            cloudRenderer.render(it)
        }

        scene.firstNode<SunNode>()?.let {
            sunRenderer.render(it)
        }

        scene.firstNode<MoonNode>()?.let {
            moonRenderer.render(it)
        }

        scene.nodesOfType<WeatherNode>().forEach {
            weatherRenderer.render(it)
        }

        scene.firstNode<LightNode>()?.let {
            lightRenderer.render(it)
        }

        // AnimationController remains unchanged for now.
    }
}
