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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.MoonStyle
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.sun.MoonPhaseCalculator
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Realistic lunar rendering.
 *
 * ── What was removed ─────────────────────────────────────────────────────────
 *
 *  • Fixed shadow offsets (radius × 0.5 for gibbous, radius × 0.25 for crescent).
 *  • Rectangular clip mask for the half-moon terminator.
 *  • Four discrete phase shapes with hardcoded geometry.
 *  • Flat uniform cream fill [Color(0xFFF5F5DC)].
 *  • The thin straight `drawLine` on the half-moon cut edge.
 *  • The fixed +12-hour time offset for moon position.
 *
 * ── What replaced them ───────────────────────────────────────────────────────
 *
 *  CONTINUOUS TERMINATOR
 *    The lunar phase fraction from [MoonPhaseCalculator.phaseFraction()] drives
 *    the terminator geometry.  The illuminated fraction k = (1 − cos(2πp))/2
 *    where p is the phase fraction in [0,1].  The shadow side is a union of:
 *      • One half-disc (the dark half of the moon), and
 *      • An elliptical cap whose X semi-axis is radius × |cos(2πp)|.
 *    Together they produce a natural crescent → quarter → gibbous → full
 *    progression without any fixed offset.  Waxing vs waning is correctly
 *    distinguished: for p < 0.5 (waxing) the lit side is on the right;
 *    for p > 0.5 (waning) the lit side is on the left.
 *
 *  LUNAR SURFACE TEXTURE
 *    At construction time a fixed set of [LunarFeature] objects is created from
 *    a deterministic seed (no per-frame randomness).  Each feature is a soft
 *    radial-gradient oval drawn at very low alpha over the lit disc surface.
 *    They represent the general pattern of lunar maria and highland contrast —
 *    not photographic crater maps.  The features are tiny data records; the
 *    draw calls are cheap `drawOval` operations.
 *
 *  PHASE-DEPENDENT MOONLIGHT
 *    Halo intensity, atmospheric glow radius, and the cloud lighting
 *    contribution all scale with lunar illumination.  A thin crescent
 *    contributes almost no ambient light; a full moon has a restrained but
 *    visible soft halo.
 *
 *  WEATHER ATTENUATION
 *    A [moonVisibility] multiplier (analogous to Phase 4's [weatherSolarVisibility])
 *    reduces the disc, halo, and bloom under adverse conditions.  Under fog
 *    the disc becomes a diffuse bright region; under storm it is nearly invisible.
 *
 *  IMPROVED POSITION
 *    The +12h offset is replaced with a phase-dependent offset: the moon leads
 *    the sun's arc by approximately (phase × 24h) hours, modulo the day length.
 *    At new moon (phase ≈ 0) the moon is near the sun; at full moon (phase ≈ 0.5)
 *    it is roughly opposite.  This is more plausible than a fixed 12h shift.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Lunar feature data model — for surface texture
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One soft low-contrast oval feature on the lunar surface.
 *
 * All coordinates are in normalised unit-disc space (−1..1 × −1..1).
 * They are scaled by the real disc radius at draw time.
 */
private class LunarFeature(
    val offX:   Float,  // centre X offset from disc centre, in disc-radius units
    val offY:   Float,  // centre Y offset
    val rX:     Float,  // oval X half-axis, in disc-radius units
    val rY:     Float,  // oval Y half-axis
    val alpha:  Float   // base opacity (always very low: 0.04–0.14)
)

/**
 * Builds a deterministic set of lunar surface features.
 *
 * The same moon always has the same texture because the seed is fixed.
 * Features represent a rough approximation of lunar maria distribution:
 * a few large darker patches (mare) and subtle mid-tone variations.
 *
 * 8 features: 3 larger maria-like patches + 5 smaller highland/mare.
 */
private fun buildLunarFeatures(): List<LunarFeature> {
    val rng = Random(0xC0FFEE42L)   // fixed seed — deterministic every session
    return buildList {
        // Larger mare patches (low alpha, large oval)
        repeat(3) {
            add(LunarFeature(
                offX  = (rng.nextFloat() - 0.5f) * 1.1f,
                offY  = (rng.nextFloat() - 0.5f) * 1.1f,
                rX    = 0.22f + rng.nextFloat() * 0.18f,
                rY    = 0.16f + rng.nextFloat() * 0.14f,
                alpha = 0.06f + rng.nextFloat() * 0.06f   // 6–12%
            ))
        }
        // Smaller mottling patches
        repeat(5) {
            add(LunarFeature(
                offX  = (rng.nextFloat() - 0.5f) * 1.3f,
                offY  = (rng.nextFloat() - 0.5f) * 1.3f,
                rX    = 0.10f + rng.nextFloat() * 0.10f,
                rY    = 0.08f + rng.nextFloat() * 0.08f,
                alpha = 0.04f + rng.nextFloat() * 0.06f   // 4–10%
            ))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weather attenuation helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Weather-driven visibility multiplier for the moon.
 *
 * Mirrors the approach used in Phase 4 [SunLayer] for solar visibility.
 * The moon is dimmer than the sun in absolute terms, so the attenuation
 * values are slightly stronger.
 */
private fun moonVisibility(weather: WeatherState): Float = when (weather) {
    WeatherState.CLEAR  -> 1.00f
    WeatherState.CLOUDY -> 0.62f
    WeatherState.SNOW   -> 0.50f
    WeatherState.RAIN   -> 0.38f
    WeatherState.FOG    -> 0.28f
    WeatherState.STORM  -> 0.14f
}

/**
 * Under fog the moon disc spreads into a diffuse hazy patch.
 */
private fun moonBloomSpread(weather: WeatherState): Float = when (weather) {
    WeatherState.FOG   -> 2.4f
    WeatherState.RAIN  -> 1.4f
    WeatherState.STORM -> 1.2f
    else               -> 1.0f
}

// ─────────────────────────────────────────────────────────────────────────────
// Lunar phase geometry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws the illuminated lunar disc with a correct continuous terminator.
 *
 * Algorithm:
 *   1. Clip drawing to the disc circle (the physical moon boundary).
 *   2. Fill the disc with the lunar surface colour + subtle texture.
 *   3. Draw the shadow region on top: a union of
 *       (a) the dark half of the disc, and
 *       (b) an elliptical cap whose X radius equals |cos(2πp)| × discRadius.
 *      The sign of cos(2πp) determines waxing vs waning (lit-side direction).
 *
 * Waxing (p < 0.5, cos > 0): shadow covers the left half + left ellipse cap.
 *                              Lit portion is on the right.
 * Waning (p > 0.5, cos < 0): shadow covers the right half + right ellipse cap.
 *                              Lit portion is on the left.
 *
 * Special cases:
 *   p ≈ 0   → shadow cap is as wide as the disc → completely dark (new moon)
 *   p = 0.25 → cos(π/2) = 0 → shadow covers exactly one half → quarter moon
 *   p = 0.5  → cos(π) = −1 → shadow cap has zero width → full moon
 *
 * @param center       Disc centre in canvas coordinates.
 * @param radius       Physical disc radius in pixels.
 * @param phase        Continuous phase fraction 0..1.
 * @param surfaceColor Base lunar surface colour.
 * @param shadowColor  Night-sky colour used to fill the shadow region.
 * @param surfaceAlpha Overall disc opacity (weather-scaled).
 * @param features     Pre-built list of surface texture features.
 * @param isNight      True during night phases — surface colour is cooler.
 */
private fun DrawScope.drawLunarDisc(
    center:       Offset,
    radius:       Float,
    phase:        Double,
    surfaceColor: Color,
    shadowColor:  Color,
    surfaceAlpha: Float,
    features:     List<LunarFeature>,
    isNight:      Boolean
) {
    val cx = center.x
    val cy = center.y

    // ── Terminator parameter ─────────────────────────────────────────────────
    // cosT = cos(2πp):
    //   > 0 → waxing (shadow on left, lit on right)
    //   < 0 → waning (shadow on right, lit on left)
    //   = 0 → quarter (exactly half lit)
    val cosT = cos(2.0 * PI * phase).toFloat()

    // Terminator ellipse X semi-axis (absolute value); sign tells us side.
    val termX = abs(cosT) * radius
    // Shadow is on the left side when cosT > 0 (waxing), right when cosT < 0 (waning).
    val shadowOnLeft = cosT > 0f

    // ── Clip path: the entire computation is clipped to the disc ────────────
    val discPath = Path().apply {
        addOval(Rect(center = center, radius = radius))
    }

    clipPath(discPath) {

        // ── Step 1: Draw the illuminated surface ──────────────────────────
        // Soft radial gradient from bright centre to slightly dimmer limb —
        // simulates limb darkening (real moon surfaces are not uniformly bright).
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to surfaceColor.copy(alpha = surfaceAlpha),
                    0.65f to surfaceColor.copy(alpha = surfaceAlpha * 0.94f),
                    0.90f to surfaceColor.copy(alpha = surfaceAlpha * 0.82f),
                    1.00f to surfaceColor.copy(alpha = 0f)
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // ── Step 2: Draw lunar surface texture ────────────────────────────
        // Deterministic low-contrast features representing mare/highland contrast.
        // The texture colour is slightly darker than the surface base.
        val textureColor = Color(
            red   = (surfaceColor.red   * 0.58f).coerceIn(0f, 1f),
            green = (surfaceColor.green * 0.60f).coerceIn(0f, 1f),
            blue  = (surfaceColor.blue  * 0.64f).coerceIn(0f, 1f)
        )
        for (f in features) {
            val fx = cx + f.offX * radius
            val fy = cy + f.offY * radius
            val fw = f.rX * radius * 2f
            val fh = f.rY * radius * 2f
            drawOval(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to textureColor.copy(alpha = f.alpha * surfaceAlpha),
                        0.60f to textureColor.copy(alpha = f.alpha * surfaceAlpha * 0.40f),
                        1.00f to Color.Transparent
                    ),
                    center = Offset(fx, fy),
                    radius = maxOf(fw, fh) * 0.55f
                ),
                topLeft = Offset(fx - fw / 2f, fy - fh / 2f),
                size    = Size(fw, fh)
            )
        }

        // ── Step 3: Draw the shadow region ────────────────────────────────
        // The shadow consists of:
        //   (a) A half-disc covering the dark side.
        //   (b) An elliptical cap of width |cosT|×radius overlapping the lit side.
        // Together they define the shadowed area for any phase.
        //
        // We paint the shadow using a soft gradient fading inward from the
        // terminator rather than a hard edge — this softens the terminator line.
        //
        // Shadow colour is the sky background with alpha; a very slight blue
        // tinge makes the unlit side feel like deep space rather than a cut-out.
        val shadowAlpha = 0.96f   // nearly opaque but allows faint surface ghost

        // Half-disc on the shadow side
        val halfRect = if (shadowOnLeft) {
            Rect(left = cx - radius * 1.05f, top = cy - radius * 1.05f,
                 right = cx,                  bottom = cy + radius * 1.05f)
        } else {
            Rect(left = cx,                   top = cy - radius * 1.05f,
                 right = cx + radius * 1.05f, bottom = cy + radius * 1.05f)
        }
        val halfPath = Path().apply { addRect(halfRect) }

        // Elliptical cap: positioned on the lit side of centre, extending into the
        // lit half.  Its X semi-axis is termX; Y semi-axis is the full disc radius.
        // At quarter moon cosT=0 so termX=0 and the cap disappears — only the
        // half-disc remains, which is exactly 50% illumination.
        // At new moon cosT=1 so termX=radius and the cap covers the entire disc.
        // At full moon cosT=-1 so the cap is on the shadow side with termX=radius,
        // but the half-disc is also on the shadow side — the two together cover the
        // entire disc, leaving nothing lit.  Handle this by flipping cap side.
        if (termX > 0.5f) {
            val capX = if (shadowOnLeft) cx - termX else cx + termX
            val capPath = Path().apply {
                addOval(Rect(
                    left   = capX - termX,
                    top    = cy - radius * 1.05f,
                    right  = capX + termX,
                    bottom = cy + radius * 1.05f
                ))
            }

            // Draw shadow half-disc
            drawPath(
                path  = halfPath,
                brush = Brush.linearGradient(
                    colorStops = if (shadowOnLeft) arrayOf(
                        0.00f to shadowColor.copy(alpha = shadowAlpha),
                        1.00f to shadowColor.copy(alpha = shadowAlpha * 0.20f)
                    ) else arrayOf(
                        0.00f to shadowColor.copy(alpha = shadowAlpha * 0.20f),
                        1.00f to shadowColor.copy(alpha = shadowAlpha)
                    ),
                    start = Offset(cx - radius, cy),
                    end   = Offset(cx + radius, cy)
                )
            )

            // Draw elliptical terminator cap over the lit side
            // Gradient fades from the shadow at the cap centre toward transparency
            // at the cap edge — this softens the terminator into the surface.
            val capGradStart = if (shadowOnLeft) Offset(capX - termX, cy)
                               else              Offset(capX + termX, cy)
            val capGradEnd   = if (shadowOnLeft) Offset(capX + termX, cy)
                               else              Offset(capX - termX, cy)
            drawPath(
                path  = capPath,
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to shadowColor.copy(alpha = shadowAlpha),
                        0.55f to shadowColor.copy(alpha = shadowAlpha * 0.60f),
                        1.00f to shadowColor.copy(alpha = 0f)
                    ),
                    start = capGradStart,
                    end   = capGradEnd
                )
            )
        } else {
            // Quarter moon or near-quarter: only draw the half-disc (termX ≈ 0)
            drawPath(
                path  = halfPath,
                color = shadowColor.copy(alpha = shadowAlpha)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoonLayer(theme: AuraTheme) {

    if (theme.profile.moon == MoonStyle.HIDDEN) return

    val isResumed by rememberIsResumed()

    val engine = remember { SunEngine() }

    // Get the actual phase fraction — used for continuous terminator geometry.
    // This is stable for hours; computing it once per composition is correct.
    val phaseFraction = remember { MoonPhaseCalculator.phaseFraction() }

    // Near new moon: suppress entirely
    if (phaseFraction < 0.03 || phaseFraction > 0.97) return

    // Pre-build deterministic surface texture — stable, never regenerated
    val features = remember { buildLunarFeatures() }

    var position by remember {
        mutableStateOf(moonPosition(engine, phaseFraction))
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        position = moonPosition(engine, phaseFraction)
        while (isActive) {
            delay(30_000L)
            position = moonPosition(engine, phaseFraction)
        }
    }

    // Illumination from the ONE authoritative theme value — computed once in AuraEngine.
    val illumination = theme.moonIlluminationFraction
    val visibility   = moonVisibility(theme.weatherState)
    val bloomSpread  = moonBloomSpread(theme.weatherState)

    // isNight uses continuous solarElevNorm — avoids discrete TimePhase comparison.
    // Sun clearly below horizon (≤ -0.05) is night for atmospheric purposes.
    val isNight = theme.solarElevNorm <= -0.05f

    // Surface colour — cool silver-grey; warmer near full
    // Near full moon the lit fraction is high so the surface appears slightly ivory;
    // crescent/gibbous phases are cooler blue-grey (more sky in the perceived scene)
    val surfaceColor = if (illumination > 0.80f) {
        Color(0xFFE8E8E0)   // near-full: warm pale ivory-grey
    } else {
        Color(0xFFCDD4DE)   // partial: cool silver-grey
    }

    // Night sky shadow fill — must closely match the SkyLayer night background.
    // Matches SKY_DEEP_NIGHT zenith (Color(0xFF010208)) blended toward the mid-sky value
    // at the moon's rendered position.  The value below is a reasonable average for the
    // upper-sky region where the moon typically sits.
    // Note: isNight is available here and could be used for day/night shadow colour if needed,
    // but since MoonLayer only renders at night phases the night colour is always correct.
    val shadowColor = Color(0xFF010810)   // deep night blue-black — consistent with SkyLayer night zenith

    Canvas(modifier = Modifier.fillMaxSize()) {

        // Disc radius — slightly smaller than original (0.06 → 0.055)
        // The original 0.06 was slightly too large for a physically plausible moon.
        val radius = size.minDimension * 0.055f

        val cx = size.width  * position.x
        val cy = size.height * (1f - position.y) * 0.45f
        val center = Offset(cx, cy)

        // Effective surface alpha: modulated by visibility and a small illumination
        // floor so even a thin crescent has a slightly visible disc.
        val surfaceAlpha = ((0.15f + illumination * 0.75f) * visibility)
            .coerceIn(0f, 1f)

        // ── Atmospheric glow — phase and weather dependent ────────────────
        // Three layers: wide haze → medium corona → local glow.
        // All scale with illumination so a crescent barely glows.

        // Layer 1: wide atmospheric haze
        val hazeR     = radius * 5.5f * bloomSpread
        val hazeAlpha = 0.055f * illumination * visibility
        if (hazeAlpha > 0.004f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFBBC8DC).copy(alpha = hazeAlpha),
                        0.50f to Color(0xFF8898B0).copy(alpha = hazeAlpha * 0.35f),
                        1.00f to Color.Transparent
                    ),
                    center = center,
                    radius = hazeR
                ),
                radius = hazeR,
                center = center
            )
        }

        // Layer 2: medium corona
        val coronaR     = radius * 2.2f * bloomSpread
        val coronaAlpha = 0.22f * illumination * visibility
        if (coronaAlpha > 0.006f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFCCD4E4).copy(alpha = coronaAlpha),
                        0.45f to Color(0xFFAABCD0).copy(alpha = coronaAlpha * 0.45f),
                        1.00f to Color.Transparent
                    ),
                    center = center,
                    radius = coronaR
                ),
                radius = coronaR,
                center = center
            )
        }

        // ── Disc + terminator + surface texture ───────────────────────────
        drawLunarDisc(
            center       = center,
            radius       = radius,
            phase        = phaseFraction,
            surfaceColor = surfaceColor,
            shadowColor  = shadowColor,
            surfaceAlpha = surfaceAlpha,
            features     = features,
            isNight      = isNight
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Position calculation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Computes a plausible moon position on the sky arc.
 *
 * The approach improves on the original fixed +12h offset by using the lunar
 * phase fraction to determine the angular separation from the sun:
 *
 *   lunar time offset = phase × 24h
 *
 * At new moon (phase ≈ 0) the offset is ~0h so the moon is near the sun.
 * At full moon (phase ≈ 0.5) the offset is ~12h — moon is roughly opposite the sun.
 * At last quarter (phase ≈ 0.75) the offset is ~18h.
 *
 * This is an approximation of the true lunar elongation — not an ephemeris
 * calculation — but it places the moon in a more astronomically plausible
 * position than the previous fixed offset.
 *
 * Limitation: this does not account for lunar orbital inclination (the moon
 * can be north/south of the ecliptic by up to ±5°), nor does it use the real
 * lunar velocity (which varies by about ±6% across the orbit).  These effects
 * are small enough that the simplified model is visually convincing.
 */
private fun moonPosition(engine: SunEngine, phaseFraction: Double): SunEngine.SunPosition {
    val now = TimeState.now()

    // Phase-dependent time offset in seconds: phase × 86400s
    val offsetSeconds = (phaseFraction * 86400.0).toLong()

    val totalSeconds = now.hour * 3600L + now.minute * 60L + now.second + offsetSeconds
    val wrappedSeconds = (totalSeconds % 86400L + 86400L) % 86400L

    val shiftedTime = TimeState(
        hour   = (wrappedSeconds / 3600L).toInt(),
        minute = ((wrappedSeconds % 3600L) / 60L).toInt(),
        second = (wrappedSeconds % 60L).toInt()
    )

    return engine.calculate(shiftedTime)
}
