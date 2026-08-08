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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.model.TimePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Mutable class — updated in-place to avoid per-tick data-class copy allocations
private class FallingBit(
    var x: Float,
    var y: Float,
    val fallSpeed: Float,
    val drift: Float,
    var sway: Float,
    var spin: Float,        // current rotation angle (radians), accumulates each tick
    val rotationRate: Float // how fast it tumbles each tick
)

private class Firefly(
    var x: Float,
    var y: Float,
    var phase: Float
)

// Mutable snowflake for winter frost crystals
private class FrostBit(
    var x: Float,
    var y: Float,
    val speed: Float,
    val drift: Float,
    val size: Float,
    var spin: Float
)

/**
 * Renders subtle seasonal ambience:
 *  - Spring: pink oval petals with tumble rotation
 *  - Autumn: amber oval leaves with tumble rotation
 *  - Summer night: twinkling fireflies
 *  - Winter: frost crystal bits (small white spinning ovals)
 *
 * Particles are mutable classes updated in-place — no per-tick allocation.
 */
@Composable
fun SeasonLayer(theme: AuraTheme) {

    val season = theme.profile.season

    val isNight = theme.timePhase == TimePhase.NIGHT ||
            theme.timePhase == TimePhase.MIDNIGHT ||
            theme.timePhase == TimePhase.EVENING

    val isDaytime = theme.timePhase == TimePhase.MORNING ||
            theme.timePhase == TimePhase.NOON ||
            theme.timePhase == TimePhase.AFTERNOON

    val isClear = theme.weatherState == com.toblad.khwab.aura.model.WeatherState.CLEAR ||
            theme.weatherState == com.toblad.khwab.aura.model.WeatherState.CLOUDY

    val isResumed by rememberIsResumed()

    // Wind intensity from AnimationLayer — scales petal/leaf horizontal drift
    val windIntensity = LocalWindIntensity.current

    when {
        season == Season.SPRING && !isNight ->
            FallingBits(color = Color(0xFFFFC1E3), count = 24, isResumed = isResumed, windIntensity = windIntensity)

        season == Season.AUTUMN && !isNight ->
            FallingBits(color = Color(0xFFD08A3E), count = 24, isResumed = isResumed, windIntensity = windIntensity)

        season == Season.SUMMER && isDaytime && isClear ->
            SummerPollen(count = 8, isResumed = isResumed, windIntensity = windIntensity)

        season == Season.SUMMER && isNight ->
            Fireflies(count = 18, isResumed = isResumed)

        season == Season.WINTER ->
            WinterFrost(count = 30, isResumed = isResumed)

        else -> Unit
    }
}

@Composable
private fun FallingBits(color: Color, count: Int, isResumed: Boolean, windIntensity: Float = 0f) {

    val bits = remember {
        mutableStateListOf<FallingBit>().apply {
            repeat(count) {
                add(
                    FallingBit(
                        x            = Random.nextFloat(),
                        y            = Random.nextFloat(),
                        fallSpeed    = Random.nextFloat() * 0.15f + 0.08f,
                        drift        = Random.nextFloat() * 0.0015f - 0.00075f,
                        sway         = Random.nextFloat() * (2f * PI.toFloat()),
                        spin         = Random.nextFloat() * (2f * PI.toFloat()),
                        rotationRate = (Random.nextFloat() * 0.04f + 0.01f) *
                                if (Random.nextBoolean()) 1f else -1f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            // Wind scales horizontal drift — calm = natural gentle drift, storm = swept sideways
            val windDrift = windIntensity * 0.0008f
            for (bit in bits) {
                bit.y    += bit.fallSpeed * 0.01f
                bit.x    += bit.drift + windDrift       // wind pushes all bits in one direction
                bit.sway += 0.05f + windIntensity * 0.06f  // sway frequency scales with wind
                bit.spin += bit.rotationRate * (1f + windIntensity * 1.5f)  // tumble faster in wind
                if (bit.y > 1f) bit.y = 0f
                if (bit.x > 1f) bit.x = 0f
                if (bit.x < 0f) bit.x = 1f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bit in bits) {
            val cx = bit.x * size.width + sin(bit.sway) * 8f
            val cy = bit.y * size.height
            // Draw a small oval rotated per bit — looks like a petal or leaf
            withTransform({
                translate(cx, cy)
                rotate(Math.toDegrees(bit.spin.toDouble()).toFloat())
            }) {
                drawOval(
                    color   = color,
                    topLeft = Offset(-5f, -3f),
                    size    = Size(10f, 6f)
                )
            }
        }
    }
}

@Composable
private fun Fireflies(count: Int, isResumed: Boolean) {

    val flies = remember {
        mutableStateListOf<Firefly>().apply {
            repeat(count) {
                add(
                    Firefly(
                        x     = Random.nextFloat(),
                        y     = Random.nextFloat() * 0.6f + 0.3f,
                        phase = Random.nextFloat() * (2f * PI.toFloat())
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            for (fly in flies) {
                fly.x     = (fly.x + (Random.nextFloat() - 0.5f) * 0.002f).coerceIn(0f, 1f)
                fly.y     = (fly.y + (Random.nextFloat() - 0.5f) * 0.002f).coerceIn(0.1f, 0.95f)
                fly.phase += 0.1f
            }
            delay(60L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (fly in flies) {
            val glow = (sin(fly.phase) + 1f) / 2f
            drawCircle(
                color  = Color(0xFFCDDC39).copy(alpha = 0.3f + glow * 0.6f),
                radius = 4f + glow * 2f,
                center = Offset(fly.x * size.width, fly.y * size.height)
            )
        }
    }
}

@Composable
private fun WinterFrost(count: Int, isResumed: Boolean) {

    val bits = remember {
        mutableStateListOf<FrostBit>().apply {
            repeat(count) {
                add(
                    FrostBit(
                        x     = Random.nextFloat(),
                        y     = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.12f + 0.06f,
                        drift = Random.nextFloat() * 0.001f - 0.0005f,
                        size  = Random.nextFloat() * 4f + 2f,
                        spin  = Random.nextFloat() * (2f * PI.toFloat())
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            for (bit in bits) {
                bit.y    += bit.speed * 0.008f
                bit.x    += bit.drift
                bit.spin += 0.03f
                if (bit.y > 1f) { bit.y = 0f; bit.x = Random.nextFloat() }
                if (bit.x > 1f) bit.x = 0f
                if (bit.x < 0f) bit.x = 1f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bit in bits) {
            val cx = bit.x * size.width
            val cy = bit.y * size.height
            withTransform({
                translate(cx, cy)
                rotate(Math.toDegrees(bit.spin.toDouble()).toFloat())
            }) {
                // Six-pointed frost crystal approximated with three short lines
                val r = bit.size
                for (i in 0..2) {
                    val a = (i * 60f)
                    val rad = Math.toRadians(a.toDouble())
                    drawLine(
                        color       = Color.White.copy(alpha = 0.70f),
                        start       = Offset((-r * cos(rad)).toFloat(), (-r * sin(rad)).toFloat()),
                        end         = Offset((r * cos(rad)).toFloat(), (r * sin(rad)).toFloat()),
                        strokeWidth = 1.2f
                    )
                }
            }
        }
    }
}

// Mutable pollen/dandelion seed particle
private class PollenBit(
    var x: Float,
    var y: Float,
    val riseSpeed: Float,   // upward drift per tick (positive = up the screen, so we subtract)
    val drift: Float,       // horizontal sway per tick
    var wobble: Float,      // wobble phase accumulator
    val wobbleRate: Float,
    val wobbleAmp: Float,   // horizontal wobble amplitude (px)
    val size: Float         // rendered radius (px)
)

/**
 * Slow-rising luminous specks simulating pollen or dandelion seeds
 * drifting upward on a warm summer day.
 *
 * Shown only during SUMMER clear/cloudy daytime (MORNING, NOON, AFTERNOON).
 * A small count (8) keeps it subtle — just enough to signal summer heat
 * without competing with the sky.
 */
@Composable
private fun SummerPollen(count: Int, isResumed: Boolean, windIntensity: Float = 0f) {

    val bits = remember {
        mutableStateListOf<PollenBit>().apply {
            repeat(count) {
                add(
                    PollenBit(
                        x          = Random.nextFloat(),
                        y          = Random.nextFloat(),
                        riseSpeed  = Random.nextFloat() * 0.0006f + 0.0003f,
                        drift      = Random.nextFloat() * 0.0004f - 0.0002f,
                        wobble     = Random.nextFloat() * (2f * PI.toFloat()),
                        wobbleRate = 0.02f + Random.nextFloat() * 0.015f,
                        wobbleAmp  = 3f + Random.nextFloat() * 4f,
                        size       = 2.5f + Random.nextFloat() * 2f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            val windDrift = windIntensity * 0.0005f
            for (bit in bits) {
                bit.y      -= bit.riseSpeed            // rise upward
                bit.x      += bit.drift + windDrift    // gentle sway + wind
                bit.wobble += bit.wobbleRate
                if (bit.y < -0.05f) { bit.y = 1.05f; bit.x = Random.nextFloat() }
                if (bit.x > 1.05f)   bit.x = -0.05f
                if (bit.x < -0.05f)  bit.x = 1.05f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bit in bits) {
            val cx = bit.x * size.width + sin(bit.wobble) * bit.wobbleAmp
            val cy = bit.y * size.height
            // Soft glowing dot — warm golden-white, semi-transparent
            drawCircle(
                color  = Color(0xFFFFF9C4).copy(alpha = 0.55f),
                radius = bit.size,
                center = Offset(cx, cy)
            )
            // Bright core
            drawCircle(
                color  = Color.White.copy(alpha = 0.80f),
                radius = bit.size * 0.45f,
                center = Offset(cx, cy)
            )
        }
    }
}
