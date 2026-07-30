package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.model.CloudStyle

/**
 * Responsible for rendering the cloud layer.
 *
 * Future versions will animate cloud movement,
 * density and transitions.
 */
class CloudRenderer {

    /**
     * Returns the CloudStyle that should
     * currently be rendered.
     */
    fun render(style: CloudStyle): CloudStyle {
        return style
    }
}
