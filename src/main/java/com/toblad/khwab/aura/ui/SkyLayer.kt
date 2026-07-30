package com.toblad.khwab.aura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SkyStyle

/**
 * Renders the background sky using
 * vertical gradients.
 */
@Composable
fun SkyLayer(
    theme: AuraTheme
) {

    val colors = when (theme.profile.sky) {

        SkyStyle.PRE_DAWN ->
            listOf(
                Color(0xFF03045E),
                Color(0xFF1D3557)
            )

        SkyStyle.SUNRISE ->
            listOf(
                Color(0xFFFFB703),
                Color(0xFFFF7F50),
                Color(0xFFFFC8DD)
            )

        SkyStyle.MORNING ->
            listOf(
                Color(0xFF4FC3F7),
                Color(0xFFB3E5FC)
            )

        SkyStyle.NOON ->
            listOf(
                Color(0xFF2196F3),
                Color(0xFFBBDEFB)
            )

        SkyStyle.AFTERNOON ->
            listOf(
                Color(0xFF42A5F5),
                Color(0xFF90CAF9)
            )

        SkyStyle.SUNSET ->
            listOf(
                Color(0xFFFF7043),
                Color(0xFFAB47BC),
                Color(0xFF5E35B1)
            )

        SkyStyle.EVENING ->
            listOf(
                Color(0xFF3949AB),
                Color(0xFF7986CB)
            )

        SkyStyle.NIGHT ->
            listOf(
                Color(0xFF0D1B2A),
                Color(0xFF000814)
            )

        SkyStyle.MIDNIGHT ->
            listOf(
                Color(0xFF000000),
                Color(0xFF001233)
            )

        SkyStyle.CLOUDY ->
            listOf(
                Color(0xFF90A4AE),
                Color(0xFFCFD8DC)
            )

        SkyStyle.STORM ->
            listOf(
                Color(0xFF263238),
                Color(0xFF455A64)
            )

        SkyStyle.FOG ->
            listOf(
                Color(0xFFB0BEC5),
                Color(0xFFECEFF1)
            )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors)
            )
    )
}
