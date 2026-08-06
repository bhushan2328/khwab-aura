package com.toblad.khwab.aura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.WeatherEffectStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random
import androidx.compose.runtime.getValue
private data class RainDrop(var x: Float, var y: Float, var speed: Float)
private data class SnowFlake(var x: Float, var y: Float, var speed: Float, var drift: Float)

/**
 * Renders animated weather effects: rain, snow, fog and,
 * during storms, occasional lightning flashes. The tight
 * per-frame rain/snow ticking pauses automatically when the
 * screen isn't visible. LightningBus keeps running regardless
 * — it's shared with the ambient thunder sound.
 */
@Composable
fun WeatherLayer(
    theme: AuraTheme
) {

    val effect = theme.profile.weatherEffect
    val isStorm = effect == WeatherEffectStyle.STORM

    LaunchedEffect(isStorm, theme.profile.stormIntensity) {
        LightningBus.update(
            stormActive = isStorm,
            intensity = theme.profile.stormIntensity
        )
    }

    if (effect == WeatherEffectStyle.NONE) {
        return
    }

    val isResumed by rememberIsResumed()

    when (effect) {

        WeatherEffectStyle.RAIN,
        WeatherEffectStyle.STORM -> {
            AnimatedRain(
                intense = isStorm,
                intensity = theme.profile.stormIntensity,
                isResumed = isResumed
            )
        }

        WeatherEffectStyle.SNOW -> {
            AnimatedSnow(isResumed = isResumed)
        }

        WeatherEffectStyle.FOG -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33ECEFF1))
            )
        }

        else -> Unit
    }

    if (isStorm) {
        LightningFlash(intensity = theme.profile.stormIntensity)
    }
}

@Composable
private fun AnimatedRain(intense: Boolean, intensity: Float, isResumed: Boolean) {

    val severity = intensity.coerceIn(0f, 1f)

    val baseCount = if (intense) 90 else 45
    val dropCount = (baseCount * (0.6f + severity * 0.8f)).toInt()

    val drops = remember(dropCount) {
        mutableStateListOf<RainDrop>().apply {
            repeat(dropCount) {
                add(
                    RainDrop(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.5f +
                                (if (intense) 1.4f else 0.8f) * (0.7f + severity * 0.6f)
                    )
                )
            }
        }
    }

    LaunchedEffect(intense, dropCount, isResumed) {

        if (!isResumed) return@LaunchedEffect

        while (isActive) {
            for (i in drops.indices) {
                val drop = drops[i]
                var newY = drop.y + drop.speed * 0.02f
                if (newY > 1f) newY = 0f
                drops[i] = drop.copy(y = newY)
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (drop in drops) {
            val x = drop.x * size.width
            val y = drop.y * size.height
            drawLine(
                color = Color(0xFF90CAF9),
                start = Offset(x, y),
                end = Offset(x + 4f, y + 20f),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
private fun AnimatedSnow(isResumed: Boolean) {

    val flakes = remember {
        mutableStateListOf<SnowFlake>().apply {
            repeat(60) {
                add(
                    SnowFlake(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.3f + 0.2f,
                        drift = Random.nextFloat() * 0.002f - 0.001f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed) {

        if (!isResumed) return@LaunchedEffect

        while (isActive) {
            for (i in flakes.indices) {
                val flake = flakes[i]
                var newY = flake.y + flake.speed * 0.01f
                var newX = flake.x + flake.drift
                if (newY > 1f) newY = 0f
                if (newX > 1f) newX = 0f
                if (newX < 0f) newX = 1f
                flakes[i] = flake.copy(y = newY, x = newX)
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (flake in flakes) {
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(flake.x * size.width, flake.y * size.height)
            )
        }
    }
}

@Composable
private fun LightningFlash(intensity: Float) {

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        LightningBus.flashes.collect {
            alpha.snapTo(0.6f + intensity.coerceIn(0f, 1f) * 0.3f)
            alpha.animateTo(0f, animationSpec = tween(250))
        }
    }

    if (alpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = alpha.value))
        )
    }
}