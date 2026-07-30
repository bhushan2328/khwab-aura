package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.MoonStyle

/**
 * Responsible for rendering the moon layer.
 *
 * Future versions will animate moon phases,
 * position, glow and eclipse effects.
 */
class MoonRenderer {

    /**
     * Returns the MoonStyle that should
     * currently be rendered.
     */
    fun render(style: MoonStyle): MoonStyle {
        return style
    }
}
