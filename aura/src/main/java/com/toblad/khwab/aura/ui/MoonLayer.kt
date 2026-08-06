package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.MoonStyle
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay

/**
 * Renders the moon, moving across the sky roughly opposite
 * the sun, shaped to approximate the real current lunar
 * phase. Position polling pauses automatically when the
 * screen isn't actually visible.
 */
@Composable
fun MoonLayer(
    theme: AuraTheme
) {

    if (theme.profile.moon == MoonStyle.HIDDEN) {
        return
    }

    val isResumed by rememberIsResumed()

    var position by remember {
        mutableStateOf(moonPosition())
    }

    LaunchedEffect(isResumed) {

        if (!isResumed) return@LaunchedEffect

        position = moonPosition()

        while (true) {
            delay(30_000L)
            position = moonPosition()
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        val radius = size.minDimension * 0.06f

        val center = Offset(
            x = size.width * position.x,
            y = size.height * (1f - position.y) * 0.45f
        )

        drawCircle(
            color = Color(0xFFF5F5DC),
            radius = radius,
            center = center
        )

        val shadowShift = when (theme.profile.moon) {
            MoonStyle.GIBBOUS -> radius * 1.4f
            MoonStyle.HALF -> radius * 1.0f
            MoonStyle.CRESCENT -> radius * 0.5f
            else -> null
        }

        if (shadowShift != null) {
            drawCircle(
                color = Color(0xCC0D1B2A),
                radius = radius,
                center = center.copy(x = center.x + shadowShift)
            )
        }
    }
}

private fun moonPosition(): SunEngine.SunPosition {

    val now = TimeState.now()

    val shiftedSeconds =
        ((now.hour * 3600 + now.minute * 60 + now.second) + 12 * 3600) % (24 * 3600)

    val shiftedTime = TimeState(
        hour = shiftedSeconds / 3600,
        minute = (shiftedSeconds % 3600) / 60,
        second = shiftedSeconds % 60
    )

    return SunEngine().calculate(shiftedTime)
}