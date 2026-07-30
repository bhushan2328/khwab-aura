package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.WeatherEffectStyle

/**
 * Renders simple weather effects.
 *
 * Initial implementation supports basic rain.
 * Future versions will include snow, fog,
 * lightning, wind and particle animations.
 */
@Composable
fun WeatherLayer(
    theme: AuraTheme
) {

    val effect = theme.profile.weatherEffect

    if (effect == WeatherEffectStyle.NONE) {
        return
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        when (effect) {

            WeatherEffectStyle.LIGHT_RAIN,
            WeatherEffectStyle.HEAVY_RAIN,
            WeatherEffectStyle.THUNDERSTORM -> {

                val drops = if (effect == WeatherEffectStyle.HEAVY_RAIN) 60 else 30

                repeat(drops) { index ->

                    val x = (index.toFloat() / drops) * size.width
                    val y = (index % 12) * 70f

                    drawLine(
                        color = Color(0xFF90CAF9),
                        start = Offset(x, y),
                        end = Offset(x + 6f, y + 22f),
                        strokeWidth = 3f
                    )
                }
            }

            else -> {
                // Other weather effects will be added later.
            }
        }
    }
}
