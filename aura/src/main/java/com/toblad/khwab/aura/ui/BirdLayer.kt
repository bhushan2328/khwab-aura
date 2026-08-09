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
import androidx.compose.ui.graphics.drawscope.Stroke
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.model.WeatherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

/**
 * Distant bird silhouettes drifting across the upper sky, arranged in small V-flocks.
 *
 * Shown only when:
 *  - Time is MORNING, NOON, or AFTERNOON
 *  - Weather is CLEAR or CLOUDY (birds don't fly in storms/fog/snow)
 *
 * Each bird has:
 *  - A wing-flap phase that animates the wing arc up/down
 *  - A gentle vertical bob for gliding feel
 *  - Flock grouping: birds are placed in small V-formations (2–3 per group)
 *
 * Mutable class — no per-tick allocation.
 */

private class Bird(
    var x: Float,          // 0..1 normalised
    var y: Float,          // base y — 0..0.45 (upper sky only)
    val speed: Float,      // drift speed per tick
    val size: Float,       // wing span multiplier
    var bob: Float,        // vertical bob accumulator
    val bobRate: Float,    // how fast it bobs
    val bobAmp: Float,     // amplitude of bob (pixels)
    var flapPhase: Float,  // current wing-flap phase (radians)
    val flapRate: Float    // flap speed per tick
)

@Composable
fun BirdLayer(theme: AuraTheme) {

    val showBirds = theme.timePhase in listOf(
        TimePhase.MORNING, TimePhase.NOON, TimePhase.AFTERNOON
    ) && theme.weatherState in listOf(WeatherState.CLEAR, WeatherState.CLOUDY)

    if (!showBirds) return

    val isResumed by rememberIsResumed()

    // Flock count scales with season:
    //   AUTUMN = 3 flocks (migration season, bigger groups)
    //   SUMMER = 2 flocks (normal daytime activity)
    //   SPRING = 2 flocks + extra solo birds (dawn dispersal)
    //   WINTER = 1 flock (few birds, most have migrated)
    val flockCount = when (theme.profile.season) {
        com.toblad.khwab.aura.model.Season.AUTUMN -> 3
        com.toblad.khwab.aura.model.Season.SPRING -> 2
        com.toblad.khwab.aura.model.Season.WINTER -> 1
        else                                       -> 2   // SUMMER
    }
    val extraSoloCount = when (theme.profile.season) {
        com.toblad.khwab.aura.model.Season.AUTUMN -> 2   // migrating stragglers
        com.toblad.khwab.aura.model.Season.SPRING -> 2   // dawn dispersal
        else                                       -> 1
    }

    val birds = remember(flockCount, extraSoloCount) {
        mutableStateListOf<Bird>().apply {
            repeat(flockCount) { flockIdx ->
                val flockX     = Random.nextFloat()
                val flockY     = Random.nextFloat() * 0.30f + 0.04f
                val baseSpeed  = 0.00016f + Random.nextFloat() * 0.00012f
                val flockSize  = if (Random.nextBoolean()) 3 else 2

                // V-formation: lead bird at (0,0), then offset flankers
                val positions = buildList {
                    add(Offset(0f, 0f))  // leader
                    for (i in 1 until flockSize) {
                        // Each subsequent bird is behind and to the side
                        val side = if (i % 2 == 0) 1f else -1f
                        add(Offset(side * i * 0.018f, i * 0.012f))
                    }
                }

                for (pos in positions) {
                    add(Bird(
                        x          = (flockX + pos.x).coerceIn(0f, 1f),
                        y          = (flockY + pos.y).coerceIn(0f, 0.45f),
                        speed      = baseSpeed * (0.9f + Random.nextFloat() * 0.2f),
                        size       = Random.nextFloat() * 0.5f + 0.55f,
                        bob        = Random.nextFloat() * 6.28f,
                        bobRate    = 0.03f + Random.nextFloat() * 0.02f,
                        bobAmp     = 1.5f + Random.nextFloat() * 1.5f,
                        flapPhase  = Random.nextFloat() * 6.28f,
                        flapRate   = 0.09f + Random.nextFloat() * 0.04f
                    ))
                }
            }
            // Solo soaring birds — count varies by season
            repeat(extraSoloCount) {
                add(Bird(
                    x          = Random.nextFloat(),
                    y          = Random.nextFloat() * 0.25f + 0.05f,
                    speed      = 0.00010f + Random.nextFloat() * 0.00008f,
                    size       = 1.1f + Random.nextFloat() * 0.3f,
                    bob        = Random.nextFloat() * 6.28f,
                    bobRate    = 0.015f,
                    bobAmp     = 3.0f,
                    flapPhase  = Random.nextFloat() * 6.28f,
                    flapRate   = 0.05f   // soaring = slow lazy flap
                ))
            }
        }
    }

    LaunchedEffect(isResumed, theme.animationsEnabled) {
        if (!isResumed || !theme.animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (bird in birds) {
                bird.x         -= bird.speed
                bird.bob       += bird.bobRate
                bird.flapPhase += bird.flapRate
                if (bird.x < -0.06f) bird.x = 1.06f   // wrap right
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bird in birds) {
            val cx   = bird.x * size.width
            val cy   = bird.y * size.height + sin(bird.bob) * bird.bobAmp
            val span = size.minDimension * 0.013f * bird.size
            // flapT in -1..1 — drives how much the wing tip is lifted vs dropped
            val flapT = sin(bird.flapPhase).toFloat()
            drawBird(Offset(cx, cy), span, flapT)
        }
    }
}

/**
 * Draws a bird silhouette as two curved-arc wings meeting at the body centre.
 *
 * [flapT] in -1..1:
 *  - +1 = wings fully raised (upstroke peak)
 *  - -1 = wings fully lowered (downstroke)
 *  -  0 = neutral glide (horizontal)
 *
 * Wing arc is drawn as a quadratic bezier so the wing bends naturally.
 */
private fun DrawScope.drawBird(center: Offset, halfSpan: Float, flapT: Float) {
    val tipLift   = halfSpan * 0.50f * flapT  // positive = tip rises above body

    val leftTip   = Offset(center.x - halfSpan, center.y - tipLift)
    val rightTip  = Offset(center.x + halfSpan, center.y - tipLift)

    // Control point: mid-wing bends upward a bit for the natural arc shape
    val midLift  = halfSpan * 0.18f * flapT
    val leftCtrl  = Offset(center.x - halfSpan * 0.55f, center.y - midLift)
    val rightCtrl = Offset(center.x + halfSpan * 0.55f, center.y - midLift)

    val path = Path().apply {
        moveTo(leftTip.x, leftTip.y)
        quadraticBezierTo(leftCtrl.x, leftCtrl.y, center.x, center.y)
        quadraticBezierTo(rightCtrl.x, rightCtrl.y, rightTip.x, rightTip.y)
    }

    drawPath(
        path   = path,
        color  = Color.Black.copy(alpha = 0.32f),
        style  = Stroke(width = 1.8f)
    )

    // Tiny body dot at centre
    drawCircle(
        color  = Color.Black.copy(alpha = 0.28f),
        radius = 1.4f,
        center = center
    )
}
