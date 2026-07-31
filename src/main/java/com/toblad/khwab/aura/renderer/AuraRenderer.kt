package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SceneBuilder
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Central rendering coordinator.
 *
 * Converts an AuraTheme into a SceneGraph,
 * creates a RenderContext and delegates the
 * rendering work to the specialized renderers.
 */
class AuraRenderer(

    private val sceneBuilder: SceneBuilder = SceneBuilder(),
    private val skyRenderer: SkyRenderer = SkyRenderer(),
    private val cloudRenderer: CloudRenderer = CloudRenderer(),
    private val sunRenderer: SunRenderer = SunRenderer(),
    private val moonRenderer: MoonRenderer = MoonRenderer(),
    private val weatherRenderer: WeatherRenderer = WeatherRenderer(),
    private val lightRenderer: LightRenderer = LightRenderer(),
    private val animationController: AnimationController = AnimationController()
) {

    /**
     * Renders the complete Aura scene.
     */
    fun render(
        theme: AuraTheme
    ) {

        val scene = sceneBuilder.build(theme)

        val context = RenderContext(
            scene = scene
        )

        skyRenderer.render(context)

        cloudRenderer.render(context)

        sunRenderer.render(context)

        moonRenderer.render(context)

        weatherRenderer.render(context)

        lightRenderer.render(context)

        animationController.apply(context)
    }
}

