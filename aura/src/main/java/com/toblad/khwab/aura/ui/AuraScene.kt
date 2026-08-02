package com.toblad.khwab.aura.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Root composable for the Aura scene.
 *
 * This composable assembles all visual layers.
 */
@Composable
fun AuraScene(
    theme: AuraTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        SkyLayer(theme)
        CloudLayer(theme)
        SunLayer(theme)
        MoonLayer(theme)
        WeatherLayer(theme)
        LightLayer(theme)
        AnimationLayer(theme)
    }
}

