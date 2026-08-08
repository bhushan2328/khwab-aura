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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SunStyle
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the sun with:
 *  - A per-style core colour (dawn=orange, noon=white-yellow, sunset=deep-red)
 *  - A soft radial-gradient disc
 *  - An outer semi-transparent corona ring
 *  - Light-ray spokes at DAWN and SUNSET (low-angle sun) with slow rotation
 *  - An atmospheric horizon bloom ellipse at DAWN and SUNSET
 *
 * Low-angle sun (DAWN/SUNSET) is placed near the horizon (y ≈ 82% of screen height)
 * so it aligns with the bloom ellipse that simulates atmospheric scatter.
 */
@Composable
fun SunLayer(theme: AuraTheme) {

    if (theme.profile.sun == SunStyle.HIDDEN) return

    val isResumed by rememberIsResumed()

    // SunEngine is stateless — create once, reuse forever
    val engine = remember { SunEngine() }

    val sunriseHour = theme.sunriseHour
    val sunsetHour  = theme.sunsetHour

    var position by remember {
        mutableStateOf(engine.calculate(TimeState.now(), sunriseHour, sunsetHour))
    }

    // Slowly rotating ray angle (degrees) — full rotation every ~80 s
    var rayAngle by remember { mutableStateOf(0f) }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        position = engine.calculate(TimeState.now(), sunriseHour, sunsetHour)
        while (true) {
            delay(50L)
            // Rotate 0.05°/tick × 20 fps ≈ 1°/s → full revolution in ~6 min
            // Kept very slow so it reads as natural shimmer, not a spinning star
            rayAngle = (rayAngle + 0.05f) % 360f
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            position = engine.calculate(TimeState.now(), sunriseHour, sunsetHour)
        }
    }

    val sunStyle = theme.profile.sun

    // Core colour varies by sun style
    val coreColor = when (sunStyle) {
        SunStyle.DAWN      -> Color(0xFFFF7043)
        SunStyle.MORNING   -> Color(0xFFFFD54F)
        SunStyle.NOON      -> Color(0xFFFFF9C4)
        SunStyle.AFTERNOON -> Color(0xFFFFCA28)
        SunStyle.SUNSET    -> Color(0xFFFF5722)
        SunStyle.HIDDEN    -> Color.Transparent
    }

    val isLowAngle = sunStyle == SunStyle.DAWN || sunStyle == SunStyle.SUNSET

    Canvas(modifier = Modifier.fillMaxSize()) {

        val radius = size.minDimension * 0.08f

        // When solar data is available, SunEngine already places the sun on the correct
        // arc (y ≈ 0.82 at horizon, y ≈ 0.05 at zenith). For the circle fallback we
        // keep the previous clamping logic so it still looks reasonable.
        val center = if (theme.isSolarAccurate) {
            Offset(
                x = size.width  * position.x,
                y = size.height * position.y
            )
        } else if (isLowAngle) {
            Offset(
                x = size.width * position.x,
                y = size.height * 0.82f            // horizon line
            )
        } else {
            Offset(
                x = size.width * position.x,
                y = size.height * (1f - position.y) * 0.55f
            )
        }

        // ── Atmospheric horizon bloom (DAWN / SUNSET only) ────────────────────
        // Wide warm ellipse near the bottom of the sky simulating scatter glow
        if (isLowAngle) {
            val bloomColor = if (sunStyle == SunStyle.DAWN)
                Color(0x55FF7043) else Color(0x66FF5722)
            val bloomW = size.width * 0.80f
            val bloomH = size.height * 0.18f
            val bloomCx = size.width * 0.50f
            val bloomCy = size.height * 0.82f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(bloomColor, Color.Transparent),
                    center = Offset(bloomCx, bloomCy),
                    radius = bloomW * 0.55f
                ),
                topLeft = Offset(bloomCx - bloomW / 2f, bloomCy - bloomH / 2f),
                size    = Size(bloomW, bloomH)
            )
        }

        // ── Outer corona glow ring ────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(coreColor.copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = radius * 2.2f
            ),
            radius = radius * 2.2f,
            center = center
        )

        // ── High-sun pulsing wide corona (NOON / AFTERNOON) ──────────────────
        // A wide, very-low-alpha halo slowly breathes in/out using the
        // existing rayAngle ticker (no extra coroutine needed).
        val isHighSun = sunStyle == SunStyle.NOON || sunStyle == SunStyle.AFTERNOON
        if (isHighSun) {
            val pulse = (kotlin.math.sin(Math.toRadians(rayAngle * 3.0)) + 1.0).toFloat() / 2f
            val pulseRadius = radius * (3.5f + pulse * 1.0f)
            val pulseAlpha  = 0.06f + pulse * 0.05f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = pulseAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = pulseRadius
                ),
                radius = pulseRadius,
                center = center
            )
        }

        // ── Light-ray spokes (DAWN / SUNSET) — slowly rotating ───────────────
        // 12 tapered lines radiate from the sun centre at low-angle phases.
        // The entire set is rotated by rayAngle so they shimmer over time.
        if (isLowAngle) {
            rotate(degrees = rayAngle, pivot = center) {
                drawSunRays(center = center, radius = radius, coreColor = coreColor)
            }
        }

        // ── Inner soft disc ───────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.92f),
                    coreColor,
                    coreColor.copy(alpha = 0.0f)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

/**
 * Draws 12 thin tapered light-ray spokes around [center].
 * Alternating long/short rays give the classic sun-ray pattern.
 * The caller is responsible for applying any rotation transform.
 */
private fun DrawScope.drawSunRays(
    center: Offset,
    radius: Float,
    coreColor: Color
) {
    val rayCount = 12
    for (i in 0 until rayCount) {
        val angleDeg = i * (360f / rayCount)
        val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

        val isLong  = i % 2 == 0
        val innerR  = radius * 1.15f
        val outerR  = radius * if (isLong) 3.2f else 2.2f
        val alpha   = if (isLong) 0.13f else 0.07f

        val startX = center.x + cos(angleRad) * innerR
        val startY = center.y + sin(angleRad) * innerR
        val endX   = center.x + cos(angleRad) * outerR
        val endY   = center.y + sin(angleRad) * outerR

        drawLine(
            color       = coreColor.copy(alpha = alpha),
            start       = Offset(startX, startY),
            end         = Offset(endX, endY),
            strokeWidth = if (isLong) 3.5f else 2.0f
        )
    }
}
