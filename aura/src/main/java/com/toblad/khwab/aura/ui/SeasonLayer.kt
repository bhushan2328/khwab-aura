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
import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.model.TimePhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private data class FallingBit(
    var x: Float,
    var y: Float,
    val fallSpeed: Float,
    val drift: Float,
    var sway: Float
)

private data class Firefly(
    var x: Float,
    var y: Float,
    var phase: Float
)

/**
 * Renders subtle seasonal ambience: falling petals in spring,
 * falling leaves in autumn, fireflies on summer nights.
 * Particle ticking pauses automatically when the screen isn't
 * visible.
 */
@Composable
fun SeasonLayer(theme: AuraTheme) {

    val season = theme.profile.season

    val isNight = theme.timePhase == TimePhase.NIGHT ||
            theme.timePhase == TimePhase.MIDNIGHT ||
            theme.timePhase == TimePhase.EVENING

    val isResumed by rememberIsResumed()

    when {
        season == Season.SPRING && !isNight ->
            FallingBits(color = Color(0xFFFFC1E3), count = 24, isResumed = isResumed)

        season == Season.AUTUMN && !isNight ->
            FallingBits(color = Color(0xFFD08A3E), count = 24, isResumed = isResumed)

        season == Season.SUMMER && isNight ->
            Fireflies(count = 18, isResumed = isResumed)

        else -> Unit
    }
}

@Composable
private fun FallingBits(color: Color, count: Int, isResumed: Boolean) {

    val bits = remember {
        mutableStateListOf<FallingBit>().apply {
            repeat(count) {
                add(
                    FallingBit(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        fallSpeed = Random.nextFloat() * 0.15f + 0.08f,
                        drift = Random.nextFloat() * 0.0015f - 0.00075f,
                        sway = Random.nextFloat() * 6.28f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {

        if (!isResumed) return@LaunchedEffect

        while (isActive) {
            for (i in bits.indices) {
                val bit = bits[i]
                var newY = bit.y + bit.fallSpeed * 0.01f
                var newX = bit.x + bit.drift

                if (newY > 1f) newY = 0f
                if (newX > 1f) newX = 0f
                if (newX < 0f) newX = 1f

                bits[i] = bit.copy(
                    x = newX,
                    y = newY,
                    sway = bit.sway + 0.05f
                )
            }

            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (bit in bits) {
            val swayOffset = kotlin.math.sin(bit.sway) * 8f

            drawCircle(
                color = color,
                radius = 5f,
                center = Offset(
                    bit.x * size.width + swayOffset,
                    bit.y * size.height
                )
            )
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
                        x = Random.nextFloat(),
                        y = Random.nextFloat() * 0.6f + 0.3f,
                        phase = Random.nextFloat() * 6.28f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {

        if (!isResumed) return@LaunchedEffect

        while (isActive) {
            for (i in flies.indices) {
                val fly = flies[i]

                flies[i] = fly.copy(
                    x = (fly.x + (Random.nextFloat() - 0.5f) * 0.002f).coerceIn(0f, 1f),
                    y = (fly.y + (Random.nextFloat() - 0.5f) * 0.002f).coerceIn(0.1f, 0.95f),
                    phase = fly.phase + 0.1f
                )
            }

            delay(60L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (fly in flies) {
            val glow = (kotlin.math.sin(fly.phase) + 1f) / 2f

            drawCircle(
                color = Color(0xFFCDDC39).copy(alpha = 0.3f + glow * 0.6f),
                radius = 4f + glow * 2f,
                center = Offset(
                    fly.x * size.width,
                    fly.y * size.height
                )
            )
        }
    }
}