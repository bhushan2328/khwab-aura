package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.CloudStyle

/**
 * Renders simple clouds.
 *
 * Future versions will support animated
 * cloud movement, multiple cloud layers
 * and weather-based density.
 */
@Composable
fun CloudLayer(
    theme: AuraTheme
) {

    if (theme.profile.clouds == CloudStyle.CLEAR) {
        return
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        val cloudColor = Color(0xEEFFFFFF)

        drawCircle(
            color = cloudColor,
            radius = 42f,
            center = Offset(
                x = size.width * 0.30f,
                y = size.height * 0.22f
            )
        )

        drawCircle(
            color = cloudColor,
            radius = 52f,
            center = Offset(
                x = size.width * 0.36f,
                y = size.height * 0.20f
            )
        )

        drawCircle(
            color = cloudColor,
            radius = 42f,
            center = Offset(
                x = size.width * 0.43f,
                y = size.height * 0.22f
            )
        )
    }
}

