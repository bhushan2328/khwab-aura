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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.getValue

// Mutable class — updated in-place to avoid per-tick allocation
private class RainDrop(var x: Float, var y: Float, var speed: Float, var angle: Float)
private class SnowFlake(var x: Float, var y: Float, var speed: Float, var drift: Float)

/**
 * Renders animated weather effects: rain, snow, fog and,
 * during storms, occasional lightning flashes.
 *
 * Rain drops are angled by wind and vary in size.
 * Particles are mutable classes updated in-place to avoid GC pressure.
 */
@Composable
fun WeatherLayer(theme: AuraTheme) {

    val effect = theme.profile.weatherEffect
    val isStorm = effect == WeatherEffectStyle.STORM

    LaunchedEffect(isStorm, theme.profile.stormIntensity) {
        LightningBus.update(
            stormActive = isStorm,
            intensity = theme.profile.stormIntensity
        )
    }

    if (effect == WeatherEffectStyle.NONE) return

    val isResumed by rememberIsResumed()

    val animationsEnabled = theme.animationsEnabled

    when (effect) {
        WeatherEffectStyle.RAIN,
        WeatherEffectStyle.STORM -> {
            AnimatedRain(
                intense = isStorm,
                intensity = theme.profile.stormIntensity,
                isResumed = isResumed,
                animationsEnabled = animationsEnabled
            )
        }
        WeatherEffectStyle.SNOW -> {
            AnimatedSnow(isResumed = isResumed, animationsEnabled = animationsEnabled)
        }
        WeatherEffectStyle.FOG -> {
            AnimatedFog(isResumed = isResumed, animationsEnabled = animationsEnabled)
        }
        else -> Unit
    }

    if (isStorm) {
        LightningFlash(intensity = theme.profile.stormIntensity)
    }
}

@Composable
private fun AnimatedRain(intense: Boolean, intensity: Float, isResumed: Boolean, animationsEnabled: Boolean = true) {

    val severity = intensity.coerceIn(0f, 1f)
    val baseCount = if (intense) 90 else 45
    val dropCount = (baseCount * (0.6f + severity * 0.8f)).toInt()

    // Wind angle: heavier storms tilt rain more (up to ~30°)
    val windAngleRad = (0.20f + severity * 0.32f)

    val drops = remember(dropCount) {
        mutableStateListOf<RainDrop>().apply {
            repeat(dropCount) {
                add(
                    RainDrop(
                        x = Random.nextFloat(),
                        y = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.5f +
                                (if (intense) 1.4f else 0.8f) * (0.7f + severity * 0.6f),
                        angle = windAngleRad * (0.8f + Random.nextFloat() * 0.4f)
                    )
                )
            }
        }
    }

    LaunchedEffect(intense, dropCount, isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (drop in drops) {
                drop.y += drop.speed * 0.02f
                drop.x += sin(drop.angle) * drop.speed * 0.006f
                if (drop.y > 1f) { drop.y = 0f; drop.x = Random.nextFloat() }
                if (drop.x > 1f) drop.x = 0f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (drop in drops) {
            val x = drop.x * size.width
            val y = drop.y * size.height
            val len = 12f + drop.speed * 4f
            val strokeW = 1.5f + drop.speed * 0.5f
            drawLine(
                color = Color(0xFF90CAF9).copy(alpha = 0.55f + drop.speed * 0.08f),
                start = Offset(x, y),
                end   = Offset(x + sin(drop.angle) * len, y + cos(drop.angle) * len),
                strokeWidth = strokeW.coerceIn(1f, 3.5f)
            )
        }
    }
}

@Composable
private fun AnimatedSnow(isResumed: Boolean, animationsEnabled: Boolean = true) {

    val flakes = remember {
        mutableStateListOf<SnowFlake>().apply {
            repeat(60) {
                add(
                    SnowFlake(
                        x     = Random.nextFloat(),
                        y     = Random.nextFloat(),
                        speed = Random.nextFloat() * 0.3f + 0.2f,
                        drift = Random.nextFloat() * 0.002f - 0.001f
                    )
                )
            }
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (flake in flakes) {
                flake.y += flake.speed * 0.01f
                flake.x += flake.drift
                if (flake.y > 1f) flake.y = 0f
                if (flake.x > 1f) flake.x = 0f
                if (flake.x < 0f) flake.x = 1f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (flake in flakes) {
            drawCircle(
                color  = Color.White.copy(alpha = 0.80f),
                radius = 3f + flake.speed * 2f,
                center = Offset(flake.x * size.width, flake.y * size.height)
            )
        }
    }
}

// Fog band — animated horizontal bands at different depths
private class FogBand(var x: Float, val y: Float, val alpha: Float, val speed: Float, val width: Float)

@Composable
private fun AnimatedFog(isResumed: Boolean, animationsEnabled: Boolean = true) {

    val bands = remember {
        mutableStateListOf<FogBand>().apply {
            listOf(0.25f, 0.48f, 0.70f).forEachIndexed { i, yFrac ->
                add(FogBand(
                    x     = Random.nextFloat(),
                    y     = yFrac,
                    alpha = 0.14f + i * 0.06f,
                    speed = 0.00012f + i * 0.00008f,
                    width = 0.65f + i * 0.15f
                ))
            }
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (band in bands) {
                band.x += band.speed
                if (band.x > 1f) band.x = -band.width
            }
            delay(16L)
        }
    }

    // Static base fog tint
    Box(modifier = Modifier.fillMaxSize().background(Color(0x22ECEFF1)))

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (band in bands) {
            val w = size.width * band.width
            val h = size.height * 0.09f
            val cx = band.x * size.width
            val cy = band.y * size.height
            // Soft horizontal fog streak using a wide ellipse
            drawOval(
                color  = Color(0xFFECEFF1).copy(alpha = band.alpha),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size    = androidx.compose.ui.geometry.Size(w, h)
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
