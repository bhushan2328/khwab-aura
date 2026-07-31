package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.MoonStyle

/**
 * Renders the moon.
 *
 * Initial implementation draws a simple moon.
 * Future milestones will support moon phases,
 * glow and eclipse effects.
 */
@Composable
fun MoonLayer(
    theme: AuraTheme
) {

    if (theme.profile.moon == MoonStyle.HIDDEN) {
        return
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        val radius = size.minDimension * 0.06f

        val center = Offset(
            x = size.width * 0.80f,
            y = size.height * 0.22f
        )

        drawCircle(
            color = Color(0xFFF5F5DC),
            radius = radius,
            center = center
        )
    }
}

