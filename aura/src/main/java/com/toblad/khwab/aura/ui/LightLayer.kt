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
import com.toblad.khwab.aura.engine.LightingEngine
import com.toblad.khwab.aura.model.AmbientLightStyle
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.world.TimeState

/**
 * Renders ambient lighting over the scene.
 *
 * Two contributions are blended together:
 *  1. A time-of-day colour tint (e.g. warm orange at sunset, deep blue at night)
 *  2. A darkening overlay whose alpha is derived from LightingEngine intensity,
 *     so overcast, rainy and stormy scenes are visibly darker than clear ones.
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

    // ── 2. Intensity darkening from LightingEngine ────────────────────────────
    // LightingEngine computes how bright the scene is (0 = very dark, 1 = full sun).
    // We invert it to get a darkening alpha: bright sun → nearly zero darkening,
    // heavy storm → up to ~0.40 darkening.
    val lightingEngine = LightingEngine()
    val lightingState  = lightingEngine.update(
        time    = TimeState.now(),
        weather = theme.profile.weatherEffect.toWorldWeather()
    )

    // Clamp darkening to 0..0.40 — we don't want to black out the entire screen
    val darkenAlpha = ((1f - lightingState.intensity) * 0.40f).coerceIn(0f, 0.40f)

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

// Maps WeatherEffectStyle → world WeatherState for LightingEngine
private fun com.toblad.khwab.aura.model.WeatherEffectStyle.toWorldWeather():
        com.toblad.khwab.aura.model.WeatherState {
    return when (this) {
        com.toblad.khwab.aura.model.WeatherEffectStyle.NONE  -> com.toblad.khwab.aura.model.WeatherState.CLEAR
        com.toblad.khwab.aura.model.WeatherEffectStyle.RAIN  -> com.toblad.khwab.aura.model.WeatherState.RAIN
        com.toblad.khwab.aura.model.WeatherEffectStyle.SNOW  -> com.toblad.khwab.aura.model.WeatherState.SNOW
        com.toblad.khwab.aura.model.WeatherEffectStyle.FOG   -> com.toblad.khwab.aura.model.WeatherState.FOG
        com.toblad.khwab.aura.model.WeatherEffectStyle.STORM -> com.toblad.khwab.aura.model.WeatherState.STORM
    }
}
