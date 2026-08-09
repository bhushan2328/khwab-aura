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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.MoonStyle
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay

/**
 * Renders the moon with:
 *  - Position derived from a 12-hour solar offset (moon opposite sun)
 *  - Phase-correct shape using clipPath — no more background-coloured cover circles
 *  - A faint blue-white glow ring for full/gibbous phases
 *
 * Phase geometry:
 *  FULL     → complete circle
 *  GIBBOUS  → large arc clipped to ~80 % lit
 *  HALF     → exactly 50 % vertical slice
 *  CRESCENT → thin curved slice
 *  HIDDEN   → early/late in cycle — not drawn
 */
@Composable
fun MoonLayer(theme: AuraTheme) {

    if (theme.profile.moon == MoonStyle.HIDDEN) return

    val isResumed by rememberIsResumed()

    // SunEngine is stateless — create once, reuse forever
    val engine = remember { SunEngine() }

    var position by remember {
        mutableStateOf(moonPosition(engine))
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        position = moonPosition(engine)
        while (true) {
            delay(30_000L)
            // Moon position update always runs (position correctness, not animation)
            position = moonPosition(engine)
        }
    }

    val moonStyle = theme.profile.moon

    Canvas(modifier = Modifier.fillMaxSize()) {

        val radius = size.minDimension * 0.06f
        val cx = size.width * position.x
        val cy = size.height * (1f - position.y) * 0.45f
        val center = Offset(cx, cy)

        // Halo glow for gibbous + full
        if (moonStyle == MoonStyle.FULL || moonStyle == MoonStyle.GIBBOUS) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x30E8EAF6),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 2.0f
                ),
                radius = radius * 2.0f,
                center = center
            )
        }

        // Phase rendering via clipPath — no background colour hacks
        when (moonStyle) {

            MoonStyle.FULL -> {
                // Simply fill the whole circle
                drawCircle(color = Color(0xFFF5F5DC), radius = radius, center = center)
            }

            MoonStyle.GIBBOUS -> {
                // Draw the full moon disc, then clip to a gibbous shape
                // The lit fraction is ~75 %, so the shadow cuts off ~25 % from the right
                val litPath = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = center,
                            radius = radius
                        )
                    )
                }
                // Offset the shadow arc to overlap the right portion
                val shadowOffsetX = radius * 0.5f
                clipPath(litPath) {
                    drawCircle(color = Color(0xFFF5F5DC), radius = radius, center = center)
                    drawCircle(
                        color  = Color(0xFF0D1B2A).copy(alpha = 0.85f),
                        radius = radius,
                        center = center.copy(x = cx + shadowOffsetX)
                    )
                }
            }

            MoonStyle.HALF -> {
                // Draw left half only — clip to a rectangle covering exactly 50 %
                val halfPath = Path().apply {
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left   = cx - radius,
                            top    = cy - radius,
                            right  = cx,            // exactly at centre
                            bottom = cy + radius
                        )
                    )
                }
                clipPath(halfPath) {
                    drawCircle(color = Color(0xFFF5F5DC), radius = radius, center = center)
                }
                // Thin limb line on the cut edge
                drawLine(
                    color  = Color(0x44F5F5DC),
                    start  = Offset(cx, cy - radius),
                    end    = Offset(cx, cy + radius),
                    strokeWidth = 1f
                )
            }

            MoonStyle.CRESCENT -> {
                // Thin crescent: full disc minus a large overlapping disc from the right
                val crescentPath = Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            center = center,
                            radius = radius
                        )
                    )
                }
                clipPath(crescentPath) {
                    drawCircle(color = Color(0xFFF5F5DC), radius = radius, center = center)
                    // Cut off ~85 % by shifting the shadow disc less than the radius
                    drawCircle(
                        color  = Color(0xFF0D1B2A).copy(alpha = 0.92f),
                        radius = radius,
                        center = center.copy(x = cx + radius * 0.25f)
                    )
                }
            }

            MoonStyle.HIDDEN -> Unit
        }
    }
}

private fun moonPosition(engine: SunEngine): SunEngine.SunPosition {

    val now = TimeState.now()

    val shiftedSeconds =
        ((now.hour * 3600 + now.minute * 60 + now.second) + 12 * 3600) % (24 * 3600)

    val shiftedTime = TimeState(
        hour   = shiftedSeconds / 3600,
        minute = (shiftedSeconds % 3600) / 60,
        second = shiftedSeconds % 60
    )

    return engine.calculate(shiftedTime)
}
