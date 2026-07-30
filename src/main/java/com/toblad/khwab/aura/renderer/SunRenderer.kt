package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.SunStyle

/**
 * Responsible for rendering the sun layer.
 *
 * Future versions will animate sun movement,
 * glow, brightness and atmospheric scattering.
 */
class SunRenderer {

    /**
     * Returns the SunStyle that should
     * currently be rendered.
     */
    fun render(style: SunStyle): SunStyle {
        return style
    }
}
