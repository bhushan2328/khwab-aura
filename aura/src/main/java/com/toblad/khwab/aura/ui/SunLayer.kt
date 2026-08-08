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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SunStyle
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay

/**
 * Renders the sun with:
 *  - A per-style core colour (dawn=orange, noon=white-yellow, sunset=deep-red)
 *  - A soft radial-gradient disc
 *  - An outer semi-transparent corona ring
 *
 * Position is updated every 30 s, engine is reused via remember.
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

    // Core colour varies by sun style
    val coreColor = when (theme.profile.sun) {
        SunStyle.DAWN      -> Color(0xFFFF7043)   // deep orange
        SunStyle.MORNING   -> Color(0xFFFFD54F)   // warm yellow
        SunStyle.NOON      -> Color(0xFFFFF9C4)   // near-white yellow
        SunStyle.AFTERNOON -> Color(0xFFFFCA28)   // golden
        SunStyle.SUNSET    -> Color(0xFFFF5722)   // deep red-orange
        SunStyle.HIDDEN    -> Color.Transparent
    }

    Canvas(modifier = Modifier.fillMaxSize()) {

        val radius = size.minDimension * 0.08f
        val center = Offset(
            x = size.width * position.x,
            y = size.height * (1f - position.y) * 0.45f
        )

        // Outer corona glow ring — large, very transparent
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 2.2f
            ),
            radius = radius * 2.2f,
            center = center
        )

        // Inner soft disc with radial gradient (bright centre → transparent edge)
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
