package com.toblad.khwab.aura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.toblad.khwab.aura.model.AnimationStyle
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Wind intensity in the range 0..1.
 *
 * 0 = no wind (CALM / NONE)
 * 1 = maximum wind (STORM)
 *
 * CloudLayer and SeasonLayer read this to scale their drift speed,
 * giving all weather-driven animations a unified wind budget.
 */
val LocalWindIntensity = compositionLocalOf { 0f }

/**
 * Coordinates scene-wide animation signals.
 *
 * Derives a wind intensity from the current [AnimationStyle] and
 * exposes it as [LocalWindIntensity] for child layers. The value
 * transitions smoothly via [Animatable] so wind doesn't snap.
 *
 * Child layers (CloudLayer, SeasonLayer) consume [LocalWindIntensity]
 * to scale their movement speed proportionally.
 */
@Composable
fun AnimationLayer(
    theme: AuraTheme,
    content: @Composable () -> Unit = {}
) {
    val targetWind = when (theme.profile.animation) {
        AnimationStyle.NONE   -> 0.00f
        AnimationStyle.CALM   -> 0.10f
        AnimationStyle.BREEZY -> 0.25f
        AnimationStyle.NORMAL -> 0.40f
        AnimationStyle.WINDY  -> 0.65f
        AnimationStyle.RAIN   -> 0.50f
        AnimationStyle.SNOW   -> 0.20f
        AnimationStyle.STORM  -> 1.00f
    }

    val wind = remember { Animatable(targetWind) }

    LaunchedEffect(targetWind) {
        wind.animateTo(targetWind, animationSpec = tween(durationMillis = 3000))
    }

    CompositionLocalProvider(LocalWindIntensity provides wind.value) {
        content()
    }
}
