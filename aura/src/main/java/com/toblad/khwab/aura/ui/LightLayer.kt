package com.toblad.khwab.aura.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AmbientLightStyle
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Renders ambient lighting over the scene.
 *
 * Two contributions are blended together:
 *  1. A time-of-day colour tint (e.g. warm orange at sunset, deep blue at night)
 *  2. A darkening overlay whose alpha is derived from [theme.lighting] intensity,
 *     so overcast, rainy and stormy scenes are visibly darker than clear ones.
 *
 * The [LightingState] is computed by [LightingEngine] inside [AuraEngine] — this
 * composable reads it from [AuraTheme] and renders it. It does NOT independently
 * recalculate lighting or create its own [LightingEngine].
 *
 * Both transitions are animated with a 4-second cross-fade.
 */
@Composable
fun LightLayer(theme: AuraTheme) {

    // ── 1. Time-of-day colour tint ────────────────────────────────────────────
    val tintTarget = when (theme.profile.ambientLight) {
        AmbientLightStyle.PRE_DAWN  -> Color(0x55000033)
        AmbientLightStyle.SUNRISE   -> Color(0x22FFB74D)
        AmbientLightStyle.MORNING   -> Color(0x06B3E5FC)  // faint sky-blue scatter
        AmbientLightStyle.NOON      -> Color(0x08FFF9C4)  // barely-visible warm gold
        AmbientLightStyle.AFTERNOON -> Color(0x11FFF59D)
        AmbientLightStyle.SUNSET    -> Color(0x33FF7043)
        AmbientLightStyle.EVENING   -> Color(0x332C3E50)
        AmbientLightStyle.MOONLIGHT -> Color(0x443F51B5)
        AmbientLightStyle.NIGHT     -> Color(0x66000000)
        AmbientLightStyle.OVERCAST  -> Color(0x22B0BEC5)
        AmbientLightStyle.FOG       -> Color(0x44ECEFF1)
    }

    val tint by animateColorAsState(
        targetValue  = tintTarget,
        animationSpec = tween(durationMillis = 4000),
        label        = "ambientTint"
    )

    // ── 2. Intensity darkening from authoritative LightingState ───────────────
    // theme.lighting is computed by LightingEngine inside AuraEngine — not here.
    // We invert it to get a darkening alpha: bright sun → nearly zero darkening,
    // heavy storm → up to ~0.40 darkening.
    val darkenAlpha = ((1f - theme.lighting.intensity) * 0.40f).coerceIn(0f, 0.40f)

    val darken by animateColorAsState(
        targetValue  = Color.Black.copy(alpha = darkenAlpha),
        animationSpec = tween(durationMillis = 4000),
        label        = "lightingDarken"
    )

    // ── Render ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darken)
    )
}
