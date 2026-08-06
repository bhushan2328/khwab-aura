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
import com.toblad.khwab.aura.model.SunStyle
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay

/**
 * Renders the sun, moving across the sky over the real
 * course of the day. Position polling pauses automatically
 * when the screen isn't actually visible.
 */
@Composable
fun SunLayer(
    theme: AuraTheme
) {

    if (theme.profile.sun == SunStyle.HIDDEN) {
        return
    }

    val isResumed by rememberIsResumed()

    var position by remember {
        mutableStateOf(SunEngine().calculate(TimeState.now()))
    }

    LaunchedEffect(isResumed) {

        if (!isResumed) return@LaunchedEffect

        position = SunEngine().calculate(TimeState.now())

        while (true) {
            delay(30_000L)
            position = SunEngine().calculate(TimeState.now())
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        val radius = size.minDimension * 0.08f

        val center = Offset(
            x = size.width * position.x,
            y = size.height * (1f - position.y) * 0.45f
        )

        drawCircle(
            color = Color(0xFFFFEB3B),
            radius = radius,
            center = center
        )
    }
}