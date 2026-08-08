package com.toblad.khwab.aura.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.TimePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Twinkling star field shown during night-time phases.
 *
 * ~120 stars at random positions across the upper 70 % of
 * the sky. Each star has its own phase offset so they
 * twinkle independently — updated at 60 ms intervals
 * (much slower than rain) since stars barely move.
 *
 * Pauses automatically when the screen isn't visible.
 */

private class Star(
    val x: Float,      // 0..1 normalised
    val y: Float,      // 0..0.70 normalised (upper sky only)
    val size: Float,   // base radius
    var phase: Float   // twinkle phase accumulator (radians)
)

@Composable
fun StarLayer(theme: AuraTheme) {

    val visible = theme.timePhase == TimePhase.NIGHT      ||
                  theme.timePhase == TimePhase.MIDNIGHT   ||
                  theme.timePhase == TimePhase.PRE_DAWN   ||
                  theme.timePhase == TimePhase.EVENING

    if (!visible) return

    val isResumed by rememberIsResumed()

    val stars = remember {
        mutableStateListOf<Star>().apply {
            repeat(120) {
                add(Star(
                    x     = Random.nextFloat(),
                    y     = Random.nextFloat() * 0.70f,
                    size  = Random.nextFloat() * 1.5f + 0.5f,
                    phase = Random.nextFloat() * (2f * PI.toFloat())
                ))
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            for (star in stars) {
                star.phase += 0.06f   // slow twinkle
            }
            delay(60L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (star in stars) {
            val twinkle = (sin(star.phase) + 1f) / 2f   // 0..1
            val alpha   = 0.35f + twinkle * 0.65f
            val radius  = star.size * (0.7f + twinkle * 0.5f)
            drawCircle(
                color  = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }
    }
}
