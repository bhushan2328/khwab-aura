package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.AuraTheme

/**
 * Central rendering coordinator.
 *
 * Delegates each visual layer to its
 * dedicated renderer.
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
     * Renders the complete Aura scene.
     */
    fun render(theme: AuraTheme) {

        skyRenderer.render(theme.profile.sky)

        cloudRenderer.render(theme.profile.clouds)

        sunRenderer.render(theme.profile.sun)

        moonRenderer.render(theme.profile.moon)

        weatherRenderer.render(theme.profile.weatherEffect)

        lightRenderer.render(theme.profile.ambientLight)

        animationController.apply(theme.profile.animation)
    }
}
