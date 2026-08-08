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
 *  - Light-ray spokes at DAWN and SUNSET (low-angle sun)
 *  - An atmospheric horizon bloom ellipse at DAWN and SUNSET
 */
@Composable
fun SunLayer(theme: AuraTheme) {

    if (theme.profile.sun == SunStyle.HIDDEN) return

    val isResumed by rememberIsResumed()

    // SunEngine is stateless — create once, reuse forever
    val engine = remember { SunEngine() }

    var position by remember {
        mutableStateOf(engine.calculate(TimeState.now()))
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        position = engine.calculate(TimeState.now())
        while (true) {
            delay(30_000L)
            position = engine.calculate(TimeState.now())
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
        val center = Offset(
            x = size.width * position.x,
            y = size.height * (1f - position.y) * 0.45f
        )

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

        // ── Light-ray spokes (DAWN / SUNSET) ─────────────────────────────────
        // 12 tapered lines radiate from the sun centre at low-angle phases
        if (isLowAngle) {
            drawSunRays(center = center, radius = radius, coreColor = coreColor)
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
 * Each spoke is a short transparent line radiating outward.
 * Alternating long/short rays give the classic sun-ray pattern.
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
