package com.toblad.khwab.aura.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Root composable for the Aura scene.
 *
 * Layer order (bottom → top):
 *   SkyLayer      — background gradient
 *   StarLayer     — twinkling stars (night phases only)
 *   CloudLayer    — drifting cloud puffs (wind-speed aware)
 *   SunLayer      — sun with corona
 *   MoonLayer     — moon with phase shape
 *   BirdLayer     — distant bird silhouettes (morning/noon/afternoon clear)
 *   WeatherLayer  — rain / snow / fog / lightning
 *   SeasonLayer   — petals / leaves / fireflies / frost
 *   AnimationLayer — provides LocalWindIntensity to all above layers
 *   LightLayer    — ambient light tint + lighting engine intensity (last)
 *
 * AnimationLayer wraps the visual layers as a CompositionLocalProvider
 * so CloudLayer and SeasonLayer can read LocalWindIntensity.
 */
@Composable
fun AuraScene(
    theme: AuraTheme,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkyLayer(theme)
        AnimationLayer(theme) {
            StarLayer(theme)
            CloudLayer(theme)
            SunLayer(theme)
            MoonLayer(theme)
            BirdLayer(theme)
            WeatherLayer(theme)
            SeasonLayer(theme)
        }
        LightLayer(theme)   // outside AnimationLayer so light always tints everything
    }
}
