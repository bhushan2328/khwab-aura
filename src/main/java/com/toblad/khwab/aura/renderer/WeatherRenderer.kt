package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.WeatherEffectStyle

/**
 * Responsible for rendering atmospheric
 * weather effects.
 *
 * Future versions will animate rain, snow,
 * fog, lightning, wind and other particle
 * systems.
 */
class WeatherRenderer {

    /**
     * Returns the WeatherEffectStyle that
     * should currently be rendered.
     */
    fun render(style: WeatherEffectStyle): WeatherEffectStyle {
        return style
    }
}
