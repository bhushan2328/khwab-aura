package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.SkyStyle

/**
 * Responsible for rendering the sky layer.
 *
 * At this stage it simply returns the
 * selected SkyStyle. In future milestones,
 * it will render gradients, stars, and
 * atmospheric effects.
 */
class SkyRenderer {

    /**
     * Returns the SkyStyle that should
     * currently be displayed.
     */
    fun render(style: SkyStyle): SkyStyle {
        return style
    }
}
