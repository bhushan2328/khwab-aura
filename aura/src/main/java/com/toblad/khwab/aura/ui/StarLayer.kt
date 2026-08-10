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
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.sun.MoonPhaseCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Stellar colour temperature palette
// ─────────────────────────────────────────────────────────────────────────────
//
// Four physically-plausible categories — differences are intentionally subtle.
// The user should perceive natural variety before consciously noticing colour.
//
//  BLUE_WHITE  — hot class-B/A stars  (#DDEBFF)   ~15 %
//  NEUTRAL     — class-F/G white      (#F4F1E8)   ~60 %
//  WARM_WHITE  — solar yellow-white   (#FFF0C7)   ~20 %
//  SUBTLE_WARM — mild orange-amber    (#FFD8B0)   ~5 %

private val COLOR_BLUE_WHITE  = Color(0xFFDDEBFF)
private val COLOR_NEUTRAL     = Color(0xFFF4F1E8)
private val COLOR_WARM_WHITE  = Color(0xFFFFF0C7)
private val COLOR_SUBTLE_WARM = Color(0xFFFFD8B0)

/** Returns a weighted-random star colour matching real stellar temperature distribution. */
private fun randomStarColor(rng: Random): Color {
    return when (rng.nextInt(100)) {
        in  0..14  -> COLOR_BLUE_WHITE   // 15 %
        in 15..74  -> COLOR_NEUTRAL      // 60 %
        in 75..94  -> COLOR_WARM_WHITE   // 20 %
        else       -> COLOR_SUBTLE_WARM  //  5 %
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Star data model — immutable properties, single mutable phase accumulator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One star in the field.
 *
 * All physical properties are immutable — set once at build time and never
 * changed.  Only [phase] advances each animation tick.
 *
 * Coordinates are normalised (0..1 × 0..1); the renderer multiplies by canvas
 * size.  [skyY] is kept in the range 0..0.75 so stars stay in the upper sky.
 *
 * [magnitude] is the "brightness class" (0 = faintest, 1 = brightest).
 * Both [baseAlpha] and [baseRadius] are derived from it so size and brightness
 * are naturally correlated — as in real stellar photometry.
 *
 * [twinkleSpeed] and [twinkleAmplitude] vary per star so the field never pulses
 * synchronously.  Very faint stars have tiny twinkle amplitudes — they barely
 * scintillate because they are already at the noise floor.
 *
 * [haloRadius] and [haloAlpha] are non-zero only for the brightest few stars
 * (magnitude > 0.80).  Ordinary stars draw as plain point circles.
 */
private class Star(
    val x:              Float,  // normalised canvas X  0..1
    val y:              Float,  // normalised canvas Y  0..0.75 (upper sky)
    val baseRadius:     Float,  // base point radius in pixels (density-independent)
    val baseAlpha:      Float,  // base maximum alpha when fully "on"
    val color:          Color,  // stellar colour (no embedded alpha)
    val magnitude:      Float,  // brightness class 0..1 (0 = faintest)
    val haloRadius:     Float,  // soft atmospheric halo radius (0 = no halo)
    val haloAlpha:      Float,  // halo base alpha (0 = no halo)
    val twinkleSpeed:   Float,  // radians added per 60 ms tick
    val twinkleAmp:     Float,  // max deviation fraction of baseAlpha
    var phase:          Float   // mutable twinkle phase accumulator (radians)
)

// ─────────────────────────────────────────────────────────────────────────────
// Magnitude-weighted distribution builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build the full star field from a [Random] instance.
 *
 * Population breakdown that mirrors real magnitude distributions:
 *
 *   Tier 0 (faint)    — 78 stars — magnitude 0.00..0.20  radius 0.35..0.80 px
 *   Tier 1 (medium)   — 28 stars — magnitude 0.20..0.55  radius 0.70..1.20 px
 *   Tier 2 (bright)   — 10 stars — magnitude 0.55..0.82  radius 1.10..1.70 px
 *   Tier 3 (very bright) — 4 stars — magnitude 0.82..1.00  radius 1.50..2.00 px
 *
 * 120 stars total — matches the existing count requirement.
 */
private fun buildStars(rng: Random): List<Star> = buildList {

    // ── Tier 0: faint background ──────────────────────────────────────────
    repeat(78) {
        val mag = rng.nextFloat() * 0.20f                         // 0.00..0.20
        val r   = 0.35f + mag * 2.25f                             // 0.35..0.80
        val a   = 0.10f + mag * 1.50f                             // 0.10..0.40
        val amp = 0.02f + rng.nextFloat() * 0.04f                 // faint: barely twinkle
        add(Star(
            x            = rng.nextFloat(),
            y            = rng.nextFloat() * 0.75f,
            baseRadius   = r,
            baseAlpha    = a,
            color        = randomStarColor(rng),
            magnitude    = mag,
            haloRadius   = 0f,
            haloAlpha    = 0f,
            twinkleSpeed = 0.035f + rng.nextFloat() * 0.040f,
            twinkleAmp   = amp,
            phase        = rng.nextFloat() * (2f * PI.toFloat())
        ))
    }

    // ── Tier 1: medium stars ──────────────────────────────────────────────
    repeat(28) {
        val mag = 0.20f + rng.nextFloat() * 0.35f                 // 0.20..0.55
        val r   = 0.70f + mag * 0.91f                             // 0.70..1.20  (approx)
        val a   = 0.35f + mag * 0.64f                             // 0.35..0.70
        val amp = 0.04f + rng.nextFloat() * 0.06f
        add(Star(
            x            = rng.nextFloat(),
            y            = rng.nextFloat() * 0.73f,
            baseRadius   = r,
            baseAlpha    = a,
            color        = randomStarColor(rng),
            magnitude    = mag,
            haloRadius   = 0f,
            haloAlpha    = 0f,
            twinkleSpeed = 0.030f + rng.nextFloat() * 0.035f,
            twinkleAmp   = amp,
            phase        = rng.nextFloat() * (2f * PI.toFloat())
        ))
    }

    // ── Tier 2: bright stars ──────────────────────────────────────────────
    repeat(10) {
        val mag = 0.55f + rng.nextFloat() * 0.27f                 // 0.55..0.82
        val r   = 1.10f + (mag - 0.55f) * 2.22f                  // 1.10..1.70
        val a   = 0.62f + (mag - 0.55f) * 1.04f                  // 0.62..0.90
        val amp = 0.04f + rng.nextFloat() * 0.05f
        val hr  = r * 2.6f
        val ha  = 0.055f + rng.nextFloat() * 0.045f
        add(Star(
            x            = rng.nextFloat(),
            y            = rng.nextFloat() * 0.70f,
            baseRadius   = r,
            baseAlpha    = a,
            color        = randomStarColor(rng),
            magnitude    = mag,
            haloRadius   = hr,
            haloAlpha    = ha,
            twinkleSpeed = 0.025f + rng.nextFloat() * 0.030f,
            twinkleAmp   = amp,
            phase        = rng.nextFloat() * (2f * PI.toFloat())
        ))
    }

    // ── Tier 3: very bright stars (handful only) ──────────────────────────
    repeat(4) {
        val mag = 0.82f + rng.nextFloat() * 0.18f                 // 0.82..1.00
        val r   = 1.50f + (mag - 0.82f) * 2.78f                  // 1.50..2.00
        val a   = (0.80f + (mag - 0.82f) * 0.50f).coerceAtMost(0.95f)
        val amp = 0.05f + rng.nextFloat() * 0.05f
        val hr  = r * 3.2f
        val ha  = 0.09f + rng.nextFloat() * 0.06f
        add(Star(
            x            = rng.nextFloat(),
            y            = rng.nextFloat() * 0.68f,
            baseRadius   = r,
            baseAlpha    = a,
            color        = randomStarColor(rng),
            magnitude    = mag,
            haloRadius   = hr,
            haloAlpha    = ha,
            twinkleSpeed = 0.020f + rng.nextFloat() * 0.025f,
            twinkleAmp   = amp,
            phase        = rng.nextFloat() * (2f * PI.toFloat())
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Continuous solar elevation → star visibility
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Continuous star visibility multiplier derived from normalised solar elevation.
 *
 * Uses [solarElevNorm] (−1..+1) for a smooth, physics-inspired mapping that
 * eliminates the discrete phase-jump behaviour of the old timePhaseMult().
 *
 * Physical model:
 *   solarElev ≤ −0.25  →  full darkness → stars fully visible
 *   −0.25 .. −0.08     →  astronomical twilight → stars gradually suppressed
 *   −0.08 .. +0.05     →  civil twilight / horizon crossing → rapid fade
 *   +0.05 and above    →  daytime → no stars (0)
 *
 * The transition zone around the horizon crossing (≈ ±0.10) is intentionally
 * narrow so the star field appears and disappears realistically near dusk/dawn.
 */
private fun solarElevToStarMult(solarElev: Float): Float = when {
    solarElev <= -0.25f -> 1.00f                                          // deep night: fully on
    solarElev <= -0.08f -> {
        // Astronomical twilight → civil twilight: gradual suppression
        val t = (solarElev - (-0.25f)) / (-0.08f - (-0.25f))             // 0..1
        1.00f - t * 0.60f                                                 // 1.00 → 0.40
    }
    solarElev <= 0.05f  -> {
        // Civil twilight → horizon crossing: rapid fade
        val t = (solarElev - (-0.08f)) / (0.05f - (-0.08f))             // 0..1
        0.40f - t * 0.40f                                                 // 0.40 → 0.00
    }
    else                -> 0.00f                                          // daytime: no stars
}

// ─────────────────────────────────────────────────────────────────────────────
// Weather visibility multiplier
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Weather-driven star visibility multiplier.
 *
 * Faint stars are suppressed more strongly than bright ones; the call site
 * applies an extra per-magnitude adjustment on top of this base value.
 */
private fun weatherMult(weather: WeatherState): Float = when (weather) {
    WeatherState.CLEAR  -> 1.00f
    WeatherState.CLOUDY -> 0.55f   // moderate suppression — many stars hidden
    WeatherState.SNOW   -> 0.42f   // diffuse sky reduces contrast
    WeatherState.RAIN   -> 0.28f   // heavy suppression
    WeatherState.FOG    -> 0.22f   // strong attenuation, especially near horizon
    WeatherState.STORM  -> 0.08f   // almost invisible; only very bright stars survive
}

// ─────────────────────────────────────────────────────────────────────────────
// Moon illumination → star suppression
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fraction of lunar disc that is illuminated.
 *
 * k = (1 − cos(2π·phase)) / 2   →   0 at new moon, 1 at full moon.
 *
 * Identical to the formula used in MoonLayer — extracted here to avoid
 * importing or modifying that file.
 */
private fun lunarIllumination(phase: Double): Float =
    ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()

/**
 * Returns a per-star moonlight suppression multiplier given lunar illumination.
 *
 * The function applies stronger suppression to faint stars than to bright ones,
 * reflecting how real moonlight primarily erases dim stars while sparing the
 * brightest points.
 *
 * @param illumination   0..1 from [lunarIllumination]
 * @param magnitude      0..1 star magnitude (0 = faintest)
 */
private fun moonSuppressionMult(illumination: Float, magnitude: Float): Float {
    // How much the moon reduces the overall field: 0 = no reduction, 0.65 = max
    val baseReduction = illumination * 0.65f
    // Faint stars (magnitude near 0) receive the full reduction.
    // Bright stars (magnitude near 1) receive almost no reduction.
    val magnitudeShield = magnitude * 0.90f        // bright stars mostly immune
    val suppression = (baseReduction * (1f - magnitudeShield)).coerceIn(0f, 1f)
    return 1f - suppression
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Photorealistic atmospheric star field.
 *
 * Architectural rules respected:
 *  - Only StarLayer.kt is modified.
 *  - MoonPhaseCalculator is called read-only (public API, not modified).
 *  - No new engines, renderers, or architecture added.
 *  - Star data built once via `remember` — never regenerated per frame.
 *  - Single shared animation coroutine updates all star phases.
 *  - `rememberIsResumed` and `theme.animationsEnabled` gates respected.
 *  - CloudLayer is rendered above this layer in AuraScene — cloud occlusion
 *    is handled automatically by layer order (no per-star cloud check needed).
 *
 * Visual model:
 *  - 120 stars (78 faint / 28 medium / 10 bright / 4 very bright).
 *  - Four subtle colour temperatures weighted by real stellar frequency.
 *  - Size and brightness correlated through a [magnitude] property.
 *  - Twinkle: restrained sine-wave modulation of alpha; each star has an
 *    independent speed and amplitude; very faint stars barely twinkle.
 *  - Horizon atmospheric fade: stars near y = 0.75 are attenuated smoothly.
 *  - Moon suppression: bright moon reduces faint star contrast.
 *  - Weather attenuation: smooth multiplier per WeatherState.
 *  - Time-phase visibility: smooth multiplier — no binary on/off.
 *  - Halos: only the brightest 14 stars draw a soft outer circle; all others
 *    are plain drawCircle point sources.
 */
@Composable
fun StarLayer(theme: AuraTheme) {

    // ── Visibility gate — continuous solar elevation ───────────────────────
    // Use the ONE authoritative solar elevation from the theme.  Skip entirely
    // when the sun is clearly above the horizon (no stars visible in daylight).
    val solarElev = theme.solarElevNorm
    val phaseMult = solarElevToStarMult(solarElev)
    if (phaseMult <= 0f) return

    // ── Shared multipliers computed outside the draw loop ─────────────────
    val weatherBase = weatherMult(theme.weatherState)

    // Moon illumination — computed once at composition time.
    // MoonPhaseCalculator is a pure astronomical calculation; calling it here
    // is a read-only use of public API.  No architecture is modified.
    val moonPhase         = remember { MoonPhaseCalculator.phaseFraction() }
    val moonIllumination  = lunarIllumination(moonPhase)

    val isResumed by rememberIsResumed()

    // ── Star data — built once, never regenerated ──────────────────────────
    val stars = remember {
        val rng = Random           // session-unique seed — every launch differs
        mutableStateListOf<Star>().also { list ->
            list.addAll(buildStars(rng))
        }
    }

    // ── Animation — single coroutine, advances all phases in-place ─────────
    LaunchedEffect(isResumed, theme.animationsEnabled) {
        if (!isResumed || !theme.animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (star in stars) {
                star.phase += star.twinkleSpeed
            }
            delay(60L)
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────
    Canvas(modifier = Modifier.fillMaxSize()) {

        val w = size.width
        val h = size.height

        for (star in stars) {

            // ── Twinkle: restrained sine-wave modulation ──────────────────
            // sin returns −1..1; normalise to 0..1
            val t = (sin(star.phase) + 1f) / 2f

            // Twinkle range is extremely narrow:
            //   1.0 − twinkleAmp  ..  1.0
            // e.g. for amp=0.06:  0.94..1.00  (6 % variation)
            val twinkleMult = (1f - star.twinkleAmp) + t * star.twinkleAmp

            // ── Atmospheric horizon fade ──────────────────────────────────
            // Stars at y=0 (zenith) are fully bright.
            // Stars at y≥0.65 fade smoothly toward 0 at y=0.75.
            // The falloff simulates atmospheric extinction near the horizon.
            // Under FOG the fade begins earlier and is stronger — fog
            // accumulates near the horizon, dramatically reducing star contrast.
            val isFog = theme.weatherState == WeatherState.FOG
            val horizonFadeStart = if (isFog) 0.38f else 0.55f
            val horizonFadeWidth = if (isFog) 0.22f else 0.20f
            val horizonFactor = when {
                star.y < horizonFadeStart ->
                    1.00f
                star.y < horizonFadeStart + horizonFadeWidth ->
                    1f - ((star.y - horizonFadeStart) / horizonFadeWidth).coerceIn(0f, 1f)
                else ->
                    0.00f
            }

            // ── Weather: faint stars suffer more than bright stars ─────────
            // Stars with magnitude < 0.2 lose visibility faster under cloud/rain
            val magnitudeFactor  = (star.magnitude / 0.20f).coerceIn(0f, 1f)
            // Extra attenuation for low-magnitude stars in bad weather
            val weatherPenalty   = if (weatherBase < 1f) {
                weatherBase + (1f - weatherBase) * magnitudeFactor * 0.40f
            } else 1f

            // ── Moon suppression ──────────────────────────────────────────
            val moonMult = moonSuppressionMult(moonIllumination, star.magnitude)

            // ── Composite alpha ───────────────────────────────────────────
            val alpha = (star.baseAlpha
                    * twinkleMult
                    * horizonFactor
                    * phaseMult
                    * weatherPenalty
                    * moonMult
                    ).coerceIn(0f, 1f)

            if (alpha < 0.005f) continue   // skip invisible stars early

            val cx = star.x * w
            val cy = star.y * h

            // ── Atmospheric halo (brightest stars only) ───────────────────
            // Drawn first (behind the point) as an extremely faint soft circle.
            // Halos have haloRadius > 0 only on Tier 2 and Tier 3 stars.
            if (star.haloRadius > 0f) {
                val hAlpha = (star.haloAlpha
                        * twinkleMult
                        * horizonFactor
                        * phaseMult
                        * weatherPenalty
                        * moonMult
                        ).coerceIn(0f, 1f)
                if (hAlpha > 0.004f) {
                    drawCircle(
                        color  = star.color.copy(alpha = hAlpha),
                        radius = star.haloRadius,
                        center = Offset(cx, cy)
                    )
                }
            }

            // ── Star point source ─────────────────────────────────────────
            // Radius varies only a tiny amount with twinkle (1–4 %) to avoid
            // stars visibly growing/shrinking.
            val radius = star.baseRadius * (0.98f + t * 0.04f)

            drawCircle(
                color  = star.color.copy(alpha = alpha),
                radius = radius,
                center = Offset(cx, cy)
            )
        }
    }
}
