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
 * Phase Color pass: LightLayer is now a SUBTLE darkening pass only.
 *
 * The previous behaviour applied strong coloured tints (up to 33% orange at
 * sunset, 27% indigo at moonlight, 40% black at night) that fought the
 * physically-inspired gradient system in SkyLayer.  Those tints are replaced
 * with near-transparent scene-level adjustments:
 *
 *   • Colour tints are capped at ~8 % alpha.  They carry only the faintest
 *     hint of the phase character — enough to tie the scene together without
 *     overpowering the sky gradient colours.
 *
 *   • The darkening contribution from LightingEngine is retained but capped at
 *     a maximum of 22 % (previously 40 %).  This is still enough to make a
 *     storm scene feel dim while leaving night and twilight colours to come
 *     from SkyLayer rather than from a black overlay.
 *
 * Architecture: LightLayer still reads [theme.lighting] and
 * [theme.profile.ambientLight] — no new data sources are introduced.
 * Both transitions remain animated with a 4-second cross-fade.
 */
@Composable
fun LightLayer(theme: AuraTheme) {

    // ── 1. Subtle scene-level colour hint ─────────────────────────────────────
    //
    // These values are intentionally near-transparent.  Their purpose is to
    // carry a TRACE of the ambient phase character across all layers simultaneously
    // (e.g. a hair of warm orange at SUNRISE ties sun, clouds and sky together).
    //
    // The realistic colours in SkyLayer, CloudLayer, SunLayer etc. are the
    // primary colour source.  LightLayer must NOT create a visible coloured
    // filter effect.
    //
    // Alpha budget per phase:
    //   Clear day phases  — 0x06..0x0A  (~2–4 %)
    //   Transitional      — 0x0A..0x10  (~4–6 %)
    //   Night             — 0x00        (zero — night darkness comes from SkyLayer)
    //   Weather           — 0x08..0x0E  (~3–5 %)
    val tintTarget = when (theme.profile.ambientLight) {
        AmbientLightStyle.PRE_DAWN  -> Color(0x0A000020)   // barely-visible deep blue
        AmbientLightStyle.SUNRISE   -> Color(0x0EFFA040)   // trace warm gold
        AmbientLightStyle.MORNING   -> Color(0x06B8DDF0)   // trace sky-blue scatter
        AmbientLightStyle.NOON      -> Color(0x06FFF8E0)   // trace warm daylight
        AmbientLightStyle.AFTERNOON -> Color(0x08FFE8A0)   // trace golden afternoon
        AmbientLightStyle.SUNSET    -> Color(0x10E88040)   // trace warm orange — not a filter
        AmbientLightStyle.EVENING   -> Color(0x0A203050)   // trace cool blue-grey
        AmbientLightStyle.MOONLIGHT -> Color(0x00000000)   // no tint — sky handles night colour
        AmbientLightStyle.NIGHT     -> Color(0x00000000)   // no tint — sky handles night colour
        AmbientLightStyle.OVERCAST  -> Color(0x0AB0BEC5)   // trace grey-blue cast
        AmbientLightStyle.FOG       -> Color(0x08D8E4EC)   // trace pale fog scatter
    }

    val tint by animateColorAsState(
        targetValue   = tintTarget,
        animationSpec = tween(durationMillis = 4000),
        label         = "ambientTint"
    )

    // ── 2. Scene darkening from authoritative LightingState ───────────────────
    //
    // Inverted intensity → darkening alpha.
    // Cap reduced from 0.40 to 0.22:  SkyLayer already encodes night darkness
    // in its gradient palette.  The darkening layer here is only needed for
    // the EXTRA dimming effect of heavy overcast / storm weather on top of
    // what is already in the sky gradient.
    //
    // A 22 % cap gives a meaningful storm-darkening effect while ensuring
    // the night sky colour comes from SkyLayer, not from black overlay.
    val darkenAlpha = ((1f - theme.lighting.intensity) * 0.22f).coerceIn(0f, 0.22f)

    val darken by animateColorAsState(
        targetValue   = Color.Black.copy(alpha = darkenAlpha),
        animationSpec = tween(durationMillis = 4000),
        label         = "lightingDarken"
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
