package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.AmbientLightStyle

/**
 * Responsible for rendering ambient lighting.
 *
 * Future versions will control scene brightness,
 * color temperature, bloom, shadows and lighting
 * transitions.
 */
class LightRenderer {

    /**
     * Returns the AmbientLightStyle that
     * should currently be rendered.
     */
    fun render(style: AmbientLightStyle): AmbientLightStyle {
        return style
    }
}
