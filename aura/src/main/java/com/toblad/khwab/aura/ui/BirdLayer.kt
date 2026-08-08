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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.model.WeatherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * Distant bird silhouettes drifting across the upper sky.
 *
 * Shown only when:
 *  - Time is MORNING, NOON, or AFTERNOON
 *  - Weather is CLEAR or CLOUDY (birds don't fly in storms/fog/snow)
 *
 * Each bird is a simple V-path (two curved strokes) that bobs
 * gently while gliding from right to left. Mutable class — no
 * per-tick allocation.
 */

private class Bird(
    var x: Float,        // 0..1 normalised
    val y: Float,        // 0..0.45 (upper sky only)
    val speed: Float,    // drift speed per tick
    val size: Float,     // wing span multiplier
    var bob: Float,      // vertical bob accumulator
    val bobRate: Float,  // how fast it bobs
    val bobAmp: Float    // amplitude of bob (pixels)
)

@Composable
fun BirdLayer(theme: AuraTheme) {

    val showBirds = theme.timePhase in listOf(
        TimePhase.MORNING, TimePhase.NOON, TimePhase.AFTERNOON
    ) && theme.weatherState in listOf(WeatherState.CLEAR, WeatherState.CLOUDY)

    if (!showBirds) return

    val isResumed by rememberIsResumed()

    val birds = remember {
        mutableStateListOf<Bird>().apply {
            repeat(5) {
                add(Bird(
                    x       = Random.nextFloat(),
                    y       = Random.nextFloat() * 0.38f + 0.03f,
                    speed   = 0.00018f + Random.nextFloat() * 0.00014f,
                    size    = Random.nextFloat() * 0.6f + 0.5f,  // 0.5–1.1×
                    bob     = Random.nextFloat() * 6.28f,
                    bobRate = 0.04f + Random.nextFloat() * 0.03f,
                    bobAmp  = 2.5f + Random.nextFloat() * 2.5f
                ))
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            for (bird in birds) {
                bird.x   -= bird.speed
                bird.bob += bird.bobRate
                if (bird.x < -0.05f) bird.x = 1.05f   // wrap right
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bird in birds) {
            val cx   = bird.x * size.width
            val cy   = bird.y * size.height + kotlin.math.sin(bird.bob) * bird.bobAmp
            val span = size.minDimension * 0.012f * bird.size
            drawBird(Offset(cx, cy), span)
        }
    }
}

/**
 * Draws a simple V-silhouette bird: two arced wing strokes meeting at the body.
 * The stroke is semi-transparent dark so it works on any sky brightness.
 */
private fun DrawScope.drawBird(center: Offset, halfSpan: Float) {
    val bodyY  = center.y
    val tipDip = halfSpan * 0.35f   // wing-tip dips slightly below centre

    // Left wing: arc from body centre out to left tip
    drawLine(
        color       = Color.Black.copy(alpha = 0.35f),
        start       = Offset(center.x, bodyY),
        end         = Offset(center.x - halfSpan, bodyY - tipDip),
        strokeWidth = 1.8f
    )
    // Right wing
    drawLine(
        color       = Color.Black.copy(alpha = 0.35f),
        start       = Offset(center.x, bodyY),
        end         = Offset(center.x + halfSpan, bodyY - tipDip),
        strokeWidth = 1.8f
    )
    // Tiny body dot
    drawCircle(
        color  = Color.Black.copy(alpha = 0.30f),
        radius = 1.5f,
        center = center
    )
}
