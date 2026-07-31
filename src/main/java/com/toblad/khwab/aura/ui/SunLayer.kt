package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SunStyle

/**
 * Renders the sun.
 *
 * Initial implementation draws a simple sun.
 * Future milestones will animate its position,
 * glow and movement across the sky.
 */
@Composable
fun SunLayer(
    theme: AuraTheme
) {

    if (theme.profile.sun == SunStyle.HIDDEN) {
        return
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        val radius = size.minDimension * 0.08f

        val center = Offset(
            x = size.width * 0.80f,
            y = size.height * 0.22f
        )

        drawCircle(
            color = Color(0xFFFFEB3B),
            radius = radius,
            center = center
        )
    }
}

