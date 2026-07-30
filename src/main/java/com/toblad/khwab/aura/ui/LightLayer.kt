package com.toblad.khwab.aura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AmbientLightStyle
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Renders ambient lighting over the scene.
 *
 * Future versions will replace this with
 * gradients, bloom and animated lighting.
 */
@Composable
fun LightLayer(
    theme: AuraTheme
) {

    val overlay = when (theme.profile.ambientLight) {

        AmbientLightStyle.PRE_DAWN ->
            Color(0x55000033)

        AmbientLightStyle.SUNRISE ->
            Color(0x22FFB74D)

        AmbientLightStyle.MORNING ->
            Color.Transparent

        AmbientLightStyle.NOON ->
            Color.Transparent

        AmbientLightStyle.AFTERNOON ->
            Color(0x11FFF59D)

        AmbientLightStyle.SUNSET ->
            Color(0x33FF7043)

        AmbientLightStyle.EVENING ->
            Color(0x332C3E50)

        AmbientLightStyle.MOONLIGHT ->
            Color(0x443F51B5)

        AmbientLightStyle.NIGHT ->
            Color(0x66000000)

        AmbientLightStyle.OVERCAST ->
            Color(0x22B0BEC5)

        AmbientLightStyle.FOG ->
            Color(0x44ECEFF1)
    }

    if (overlay != Color.Transparent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlay)
        )
    }
}
