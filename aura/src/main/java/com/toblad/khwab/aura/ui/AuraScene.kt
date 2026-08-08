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
 *   CloudLayer    — drifting cloud puffs
 *   SunLayer      — sun with corona
 *   MoonLayer     — moon with phase shape
 *   WeatherLayer  — rain / snow / fog / lightning
 *   SeasonLayer   — petals / leaves / fireflies / frost
 *   AnimationLayer — wind / scene-wide animation signals
 *   LightLayer    — ambient light tint + lighting engine intensity (last)
 */
@Composable
fun AuraScene(
    theme: AuraTheme,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkyLayer(theme)
        StarLayer(theme)
        CloudLayer(theme)
        SunLayer(theme)
        MoonLayer(theme)
        WeatherLayer(theme)
        SeasonLayer(theme)
        AnimationLayer(theme)
        LightLayer(theme)   // ← moved last: tints every layer beneath it
    }
}
