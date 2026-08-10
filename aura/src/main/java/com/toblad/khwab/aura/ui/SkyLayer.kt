package com.toblad.khwab.aura.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.sun.MoonPhaseCalculator
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * Atmospheric sky renderer — Phase 11 Continuous Atmospheric Color Physics.
 *
 * PRIMARY COLOUR SOURCE for the Aura scene.
 *
 * ── Phase 11 upgrade ──────────────────────────────────────────────────────────
 *
 * The sky is no longer driven by a discrete TimePhase→palette lookup.
 * Instead, colors are calculated continuously from a normalised solar elevation
 * value [solarElevNorm] in [-1, +1]:
 *
 *   -1.0  →  solar midnight  (sun at lowest point below horizon)
 *    0.0  →  horizon crossing (sunrise or sunset)
 *   +1.0  →  solar noon      (sun at highest elevation)
 *
 * This value is derived from the real sunrise/sunset hours already in AuraTheme
 * (which come from SolarCalculator + device GPS).  When no GPS data is available,
 * the fallback uses the fixed TimePhase schedule.
 *
 * The continuous calculation means that:
 *   5:42 AM → 5:43 AM → 5:44 AM  produces smoothly evolving sky colors
 * rather than:
 *   NIGHT palette → crossfade → PRE_DAWN palette
 *
 * ── Multi-stop vertical model ─────────────────────────────────────────────────
 *
 * The 5-stop vertical structure is preserved:
 *   0.00  zenith
 *   0.25  upper atmosphere
 *   0.55  mid sky
 *   0.78  lower atmosphere
 *   1.00  horizon
 *
 * Each stop is calculated independently from solarElevNorm so the horizon
 * reacts far more strongly to low-angle sunlight than the zenith.
 *
 * ── Weather modulation ────────────────────────────────────────────────────────
 *
 * A second semi-transparent overlay gradient is composited over the base sky.
 * The overlay preserves time-of-day identity — it reduces and modifies the sky
 * without replacing it.  All weather×time combinations remain recognisable.
 *
 * ── Moonlight contribution ────────────────────────────────────────────────────
 *
 * During night (solarElevNorm ≤ -0.15), lunar illumination [0..1] slightly
 * brightens and cools the atmosphere, strongest near the horizon.
 * The effect is very subtle — it must not create a visible coloured glow.
 *
 * ── Seasonal modulation ───────────────────────────────────────────────────────
 *
 * A small seasonal shift is applied to the computed base colors:
 *   WINTER: slightly cooler/desaturated
 *   SUMMER: slightly clearer/deeper daytime blue
 *   AUTUMN: very subtle warmer lower-atmosphere tendency
 *   SPRING: slight neutral-warm atmospheric warmth
 * Each adjustment is at most a few percent per channel.
 *
 * ── LightLayer relationship ───────────────────────────────────────────────────
 *
 * LightLayer (Phase Color pass) applies only trace tints (≤6% alpha).
 * SkyLayer is the authoritative source of colour for the entire scene.
 *
 * ── Performance ───────────────────────────────────────────────────────────────
 *
 * Solar elevation is computed from TimeState (current clock) at startup and
 * refreshed every 30 seconds via a LaunchedEffect — matching SunLayer's poll
 * interval.  No per-frame allocations are introduced.  The Brush is rebuilt only
 * when an animated Color state changes.
 *
 * animateColorAsState is called exactly 9 times (5 base + 4 weather) per the
 * Compose rules — never inside loops or lambdas.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Linear interpolation helpers (local aliases — shared versions in AtmosphericUtils)
// ─────────────────────────────────────────────────────────────────────────────

private fun lerp(a: Float, b: Float, t: Float): Float = lerpAtm(a, b, t)

/** Interpolates each RGB channel independently. */
private fun lerpColor(a: Color, b: Color, t: Float): Color = lerpColorAtm(a, b, t)

// ─────────────────────────────────────────────────────────────────────────────
// Atmospheric color anchor points
//
// Six named atmospheric states span the full solar cycle:
//   DEEP_NIGHT     solarElevNorm ≈ -1.0
//   TWILIGHT       solarElevNorm ≈ -0.20 .. -0.05 (civil twilight zone)
//   HORIZON        solarElevNorm ≈  0.0  (exact horizon crossing)
//   LOW_DAY        solarElevNorm ≈ +0.20 (sun well up but low)
//   FULL_DAY       solarElevNorm ≈ +0.65 (high sun — morning/afternoon)
//   SOLAR_NOON     solarElevNorm ≈ +1.0
//
// The continuous baseStops() function interpolates between these anchors
// based on the actual solarElevNorm value.
// ─────────────────────────────────────────────────────────────────────────────

/** 5-stop array: [zenith, upper, mid, lower, horizon] */
private typealias SkyPalette = Array<Color>

// ── DEEP NIGHT (solar elevation ≈ -1.0) ─────────────────────────────────────
// Near-black blue — never pure black; horizon marginally brighter
private val SKY_DEEP_NIGHT = arrayOf(
    Color(0xFF010208),   // zenith:   near-black deep space blue
    Color(0xFF020510),   // upper:    very dark blue-black
    Color(0xFF04091A),   // mid:      dark navy
    Color(0xFF070D24),   // lower:    deep dark blue
    Color(0xFF0A1230)    // horizon:  slightly lighter — more atmosphere path
)

// ── ASTRONOMICAL TWILIGHT (solar elevation ≈ -0.40) ─────────────────────────
// Upper sky still night; subtle blue darkening begins below
private val SKY_ASTRO_TWILIGHT = arrayOf(
    Color(0xFF02061A),   // zenith:   deep night blue
    Color(0xFF04102C),   // upper:    dark navy
    Color(0xFF081838),   // mid:      deep navy
    Color(0xFF0D2048),   // lower:    navy, brighter than zenith
    Color(0xFF142850)    // horizon:  atmospheric dark blue haze
)

// ── CIVIL TWILIGHT (solar elevation ≈ -0.12) ────────────────────────────────
// Blue hour: deep blue everywhere; lower atmosphere begins warming
private val SKY_CIVIL_TWILIGHT = arrayOf(
    Color(0xFF04091E),   // zenith:   deep cold night blue
    Color(0xFF080F30),   // upper:    dark blue
    Color(0xFF0E1A48),   // mid:      transitional dark blue
    Color(0xFF1A1E52),   // lower:    cool blue-violet — blue hour begins
    Color(0xFF281840)    // horizon:  faint indigo-violet — first warmth hint
)

// ── HORIZON CROSSING (solar elevation ≈ 0.0) ────────────────────────────────
// The critical sunrise/sunset moment:
// upper sky stays COOL; warmth is concentrated ONLY at the horizon
private val SKY_HORIZON_CROSS = arrayOf(
    Color(0xFF10142C),   // zenith:   dark cool blue-violet
    Color(0xFF201858),   // upper:    blue-violet
    Color(0xFF623050),   // mid:      transitional warm violet
    Color(0xFFBC5428),   // lower:    warm orange atmospheric light
    Color(0xFFDC9C50)    // horizon:  golden-peach haze at solar disc level
)

// ── LOW ANGLE (solar elevation ≈ +0.20) ─────────────────────────────────────
// Sun well above horizon — warm colours have receded upward toward mid-sky.
// This is the "early morning" or "late afternoon golden hour" state.
private val SKY_LOW_ANGLE = arrayOf(
    Color(0xFF142850),   // zenith:   deep natural blue
    Color(0xFF224070),   // upper:    medium blue
    Color(0xFF4A6898),   // mid:      lighter blue, hint of warmth
    Color(0xFF86A8C0),   // lower:    pale blue-grey, trace warm
    Color(0xFFCCD8E0)    // horizon:  pale haze
)

// ── FULL DAY (solar elevation ≈ +0.65) ──────────────────────────────────────
// Classic natural sky — deep blue zenith, pale atmospheric horizon
private val SKY_FULL_DAY = arrayOf(
    Color(0xFF0C2B62),   // zenith:   deep natural blue
    Color(0xFF184585),   // upper:    full natural blue
    Color(0xFF3468AC),   // mid:      classic sky blue
    Color(0xFF70A0CC),   // lower:    pale atmospheric blue
    Color(0xFFB8D6E4)    // horizon:  very pale haze
)

// ── SOLAR NOON (solar elevation ≈ +1.0) ─────────────────────────────────────
// Deepest daytime blue — maximum Rayleigh scattering
private val SKY_SOLAR_NOON = arrayOf(
    Color(0xFF0A2858),   // zenith:   deepest natural blue
    Color(0xFF163E80),   // upper:    rich natural blue
    Color(0xFF2E62A8),   // mid:      sky blue
    Color(0xFF6898C8),   // lower:    pale atmospheric blue
    Color(0xFFB4D0E0)    // horizon:  pale haze
)

// ─────────────────────────────────────────────────────────────────────────────
// Continuous atmospheric base stop calculation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Computes the 5 base atmosphere stops continuously from [solarElev].
 *
 * The function maps solarElev ∈ [-1, +1] to a weighted blend between
 * the named anchor palettes above.
 *
 * Mapping:
 *   elev ≤ -0.80  →  DEEP_NIGHT     (100%)
 *   -0.80..-0.35  →  DEEP_NIGHT → ASTRO_TWILIGHT
 *   -0.35..-0.08  →  ASTRO_TWILIGHT → CIVIL_TWILIGHT
 *   -0.08..+0.05  →  CIVIL_TWILIGHT → HORIZON_CROSS
 *   +0.05..+0.25  →  HORIZON_CROSS  → LOW_ANGLE
 *   +0.25..+0.70  →  LOW_ANGLE      → FULL_DAY
 *   +0.70..+1.00  →  FULL_DAY       → SOLAR_NOON
 *
 * The transition zones are chosen so that:
 *   • The horizon-crossing band is narrow (±0.07) for maximum sunrise/sunset drama
 *   • The daytime plateau is wide — sky stays stable for most of the day
 *   • Night transitions are gradual — no sudden blue-hour jump
 *
 * The horizon stop reacts MORE strongly than the zenith at low elevations.
 * This is achieved by using a nonlinear curve ([sunsetCurve]) for the lower
 * atmosphere stops during the twilight/horizon zone.
 *
 * @param solarElev  Normalised solar elevation in [-1, +1]
 * @param isNight    Whether it is currently nighttime (solarElev ≤ 0)
 */
private fun continuousBaseStops(solarElev: Float): SkyPalette {

    return when {

        // ── Deep night ────────────────────────────────────────────────────────
        solarElev <= -0.80f -> SKY_DEEP_NIGHT

        // ── Deep night → Astronomical twilight ───────────────────────────────
        solarElev <= -0.35f -> {
            val t = (solarElev - (-0.80f)) / (-0.35f - (-0.80f))
            blendPalettes(SKY_DEEP_NIGHT, SKY_ASTRO_TWILIGHT, t)
        }

        // ── Astronomical twilight → Civil twilight (blue hour) ────────────────
        solarElev <= -0.08f -> {
            val t = (solarElev - (-0.35f)) / (-0.08f - (-0.35f))
            blendPalettes(SKY_ASTRO_TWILIGHT, SKY_CIVIL_TWILIGHT, t)
        }

        // ── Civil twilight → Horizon crossing ────────────────────────────────
        // This is the critical twilight band.  The horizon stop reacts much more
        // strongly — we apply a sqrt curve to t for the lower stops so the horizon
        // warmth arrives before the upper sky changes.
        solarElev <= 0.05f -> {
            val tLinear = (solarElev - (-0.08f)) / (0.05f - (-0.08f))
            // Lower-sky (indices 3,4) get a boosted horizon curve
            val tHorizon = tLinear.pow(0.5f)   // reaches target faster at low elev
            val tUpper   = tLinear.pow(1.6f)   // upper sky lags behind
            blendPalettesNonlinear(
                SKY_CIVIL_TWILIGHT, SKY_HORIZON_CROSS,
                tUpper  = tUpper,
                tMid    = tLinear,
                tLower  = tHorizon
            )
        }

        // ── Horizon crossing → Low angle ─────────────────────────────────────
        // Warm colors "climb up" from horizon as sun rises; shrink back as sun sets.
        // Horizon recedes faster than zenith changes.
        solarElev <= 0.25f -> {
            val tLinear = (solarElev - 0.05f) / (0.25f - 0.05f)
            val tHorizon = tLinear.pow(0.6f)
            val tUpper   = tLinear.pow(1.5f)
            blendPalettesNonlinear(
                SKY_HORIZON_CROSS, SKY_LOW_ANGLE,
                tUpper  = tUpper,
                tMid    = tLinear,
                tLower  = tHorizon
            )
        }

        // ── Low angle → Full day ──────────────────────────────────────────────
        solarElev <= 0.70f -> {
            val t = (solarElev - 0.25f) / (0.70f - 0.25f)
            blendPalettes(SKY_LOW_ANGLE, SKY_FULL_DAY, t)
        }

        // ── Full day → Solar noon ─────────────────────────────────────────────
        else -> {
            val t = (solarElev - 0.70f) / (1.00f - 0.70f)
            blendPalettes(SKY_FULL_DAY, SKY_SOLAR_NOON, t)
        }
    }
}

/**
 * Blends two palettes with a single linear factor applied to all 5 stops.
 */
private fun blendPalettes(a: SkyPalette, b: SkyPalette, t: Float): SkyPalette =
    Array(5) { i -> lerpColor(a[i], b[i], t) }

/**
 * Blends two palettes with different blend factors per vertical zone.
 *
 * [tUpper] applies to stops 0,1  (zenith + upper sky)
 * [tMid]   applies to stop  2   (mid sky)
 * [tLower] applies to stops 3,4  (lower atmosphere + horizon)
 *
 * This creates the critical physical behaviour where the horizon
 * reacts much more strongly to solar elevation changes than the zenith.
 */
private fun blendPalettesNonlinear(
    a:      SkyPalette,
    b:      SkyPalette,
    tUpper: Float,
    tMid:   Float,
    tLower: Float
): SkyPalette = arrayOf(
    lerpColor(a[0], b[0], tUpper),   // zenith
    lerpColor(a[1], b[1], tUpper),   // upper sky
    lerpColor(a[2], b[2], tMid),     // mid sky
    lerpColor(a[3], b[3], tLower),   // lower atmosphere
    lerpColor(a[4], b[4], tLower)    // horizon
)

// ─────────────────────────────────────────────────────────────────────────────
// Moonlight atmospheric contribution
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adds a subtle cool atmospheric lift to night sky stops based on lunar
 * illumination.
 *
 * The effect is:
 *   - Strongest at the horizon (lower atmosphere carries more atmospheric path)
 *   - Weaker toward the zenith
 *   - Maximum total brightness lift ≈ 0.04–0.06 per channel at full moon
 *   - Zero at new moon
 *
 * This must remain extremely subtle — it brightens/cools the atmosphere without
 * creating a visible glow or tint.
 *
 * @param stops       The 5-stop array to modify in place (returns new array)
 * @param illumination Lunar illumination 0.0 (new) .. 1.0 (full)
 * @param solarElev   Solar elevation norm — contribution only below horizon
 */
private fun applyMoonlightAtmosphere(
    stops:        SkyPalette,
    illumination: Float,
    solarElev:    Float
): SkyPalette {

    // Only apply when sun is clearly below horizon
    if (solarElev > -0.05f || illumination < 0.02f) return stops

    // How far below horizon: -0.05 → 0, -1.0 → max
    val nightDepth = ((-solarElev - 0.05f) / 0.95f).coerceIn(0f, 1f)

    // Overall contribution: max at full moon deep night
    // Maximum lift: +0.05 brightness per channel at horizon (full moon, deep night)
    val maxLift = 0.05f * illumination * nightDepth

    // Per-stop lift: stronger toward horizon (stop 4) than zenith (stop 0)
    // Ratios: zenith 0.15, upper 0.30, mid 0.55, lower 0.80, horizon 1.00
    val liftRatios = floatArrayOf(0.15f, 0.30f, 0.55f, 0.80f, 1.00f)

    // Moonlight is cool-silver — slight blue-white colour cast
    // The RGB ratios produce a cool-white lift rather than a coloured tint
    val moonR = 0.75f   // slightly desaturated
    val moonG = 0.80f
    val moonB = 1.00f   // cool blue emphasis

    return Array(5) { i ->
        val lift = maxLift * liftRatios[i]
        Color(
            red   = (stops[i].red   + lift * moonR).coerceIn(0f, 1f),
            green = (stops[i].green + lift * moonG).coerceIn(0f, 1f),
            blue  = (stops[i].blue  + lift * moonB).coerceIn(0f, 1f),
            alpha = stops[i].alpha
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Seasonal atmospheric modulation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Applies a very subtle seasonal colour shift to the computed sky stops.
 *
 * Seasonal effects are intentionally small — per-channel adjustments are
 * at most ±0.025 (±2.5 %) so the effect reads as "natural variation" rather
 * than a colour filter.
 *
 * WINTER: slightly cooler (reduce red/green, maintain blue) + slight desaturation
 * SUMMER: slightly deeper daytime blue (boost blue channel during day only)
 * AUTUMN: very subtle warm lower-atmosphere tendency (trace orange in lower stops)
 * SPRING: very subtle warm-neutral atmosphere (barely perceptible)
 *
 * Season effect is only applied during daytime (solarElev > 0) for SUMMER,
 * and at all times for WINTER.  AUTUMN/SPRING are so subtle they apply always.
 */
private fun applySeasonalModulation(
    stops:     SkyPalette,
    season:    Season,
    solarElev: Float
): SkyPalette {

    val isDaytime = solarElev > 0.05f

    return when (season) {

        Season.WINTER -> {
            // Slightly cooler atmosphere — reduce warm channels marginally
            val coolR = -0.018f
            val coolG = -0.010f
            val coolB =  0.008f
            Array(5) { i ->
                Color(
                    red   = (stops[i].red   + coolR).coerceIn(0f, 1f),
                    green = (stops[i].green + coolG).coerceIn(0f, 1f),
                    blue  = (stops[i].blue  + coolB).coerceIn(0f, 1f),
                    alpha = stops[i].alpha
                )
            }
        }

        Season.SUMMER -> {
            // Slightly deeper blue during daytime only
            if (!isDaytime) return stops
            val deepBlue = 0.015f
            Array(5) { i ->
                Color(
                    red   = stops[i].red,
                    green = stops[i].green,
                    blue  = (stops[i].blue + deepBlue).coerceIn(0f, 1f),
                    alpha = stops[i].alpha
                )
            }
        }

        Season.AUTUMN -> {
            // Trace warm lower atmosphere — barely perceptible
            // Only the lower 2 stops get any warmth
            Array(5) { i ->
                val warmR = if (i >= 3) 0.012f else 0.004f
                val warmG = if (i >= 3) 0.004f else 0.002f
                Color(
                    red   = (stops[i].red   + warmR).coerceIn(0f, 1f),
                    green = (stops[i].green + warmG).coerceIn(0f, 1f),
                    blue  = stops[i].blue,
                    alpha = stops[i].alpha
                )
            }
        }

        Season.SPRING -> {
            // Very subtle neutral-warm haze — smallest of all seasonal effects
            Array(5) { i ->
                val warmR = if (i >= 3) 0.008f else 0.002f
                Color(
                    red   = (stops[i].red   + warmR).coerceIn(0f, 1f),
                    green = stops[i].green,
                    blue  = stops[i].blue,
                    alpha = stops[i].alpha
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weather modulation overlay  (4 stops: zenith, upper, lower, horizon)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the four raw colours for the weather-modulation overlay gradient.
 *
 * Phase Color principle (preserved from Phase Color):
 *   Weather must modify the continuous atmosphere, NOT replace it.
 *   The time identity must remain recognisable through ANY weather condition.
 *
 * The overlay alphas are adjusted by [solarElev] so:
 *   - Warm phases (solarElev near 0) get less suppression at the horizon
 *   - Night phases get slightly different suppression characters
 *
 * Returns null for CLEAR weather (no overlay drawn).
 */
private fun weatherOverlayStops(
    weather:    WeatherState,
    solarElev:  Float
): Array<Color>? {

    val isNight = solarElev < -0.08f
    val isWarm  = solarElev in -0.10f..0.15f   // horizon-crossing zone — preserve warmth

    return when (weather) {

        WeatherState.CLEAR -> null

        WeatherState.CLOUDY -> {
            val zenithAlpha = if (isNight) 0x40 else 0x38
            val horizAlpha  = if (isNight) 0x18 else 0x14
            arrayOf(
                Color(0xFFA8B8C4).copy(alpha = zenithAlpha / 255f),
                Color(0xFFB0BEC8).copy(alpha = 0x2E / 255f),
                Color(0xFFBEC8D0).copy(alpha = 0x1C / 255f),
                Color(0xFFCAD4DB).copy(alpha = horizAlpha / 255f)
            )
        }

        WeatherState.RAIN -> {
            val suppressTop = when {
                isWarm  -> 0x58
                isNight -> 0x68
                else    -> 0x72
            }
            val suppressBot = when {
                isWarm  -> 0x28
                isNight -> 0x38
                else    -> 0x3C
            }
            arrayOf(
                Color(0xFF7A8E9A).copy(alpha = suppressTop / 255f),
                Color(0xFF8898A4).copy(alpha = 0x58 / 255f),
                Color(0xFF92A0AA).copy(alpha = 0x3E / 255f),
                Color(0xFFA0AEBA).copy(alpha = suppressBot / 255f)
            )
        }

        WeatherState.FOG -> {
            val fogHue = if (isWarm) Color(0xFFD0C4B0) else Color(0xFFBCC8D0)
            arrayOf(
                fogHue.copy(alpha = 0x28 / 255f),
                fogHue.copy(alpha = 0x48 / 255f),
                fogHue.copy(alpha = 0x68 / 255f),
                fogHue.copy(alpha = 0x88 / 255f)
            )
        }

        WeatherState.SNOW -> {
            val top = if (isNight) 0x38 else 0x48
            arrayOf(
                Color(0xFF98AEB8).copy(alpha = top / 255f),
                Color(0xFFA6B8C4).copy(alpha = 0x38 / 255f),
                Color(0xFFB8C8D4).copy(alpha = 0x2E / 255f),
                Color(0xFFC8D8E2).copy(alpha = 0x24 / 255f)
            )
        }

        WeatherState.STORM -> {
            val topAlpha = when {
                isNight -> 0x90
                else    -> 0x7C
            }
            val botAlpha = when {
                isWarm  -> 0x38
                isNight -> 0x58
                else    -> 0x54
            }
            arrayOf(
                Color(0xFF2A3440).copy(alpha = topAlpha / 255f),
                Color(0xFF364050).copy(alpha = 0x72 / 255f),
                Color(0xFF424E5C).copy(alpha = 0x5A / 255f),
                Color(0xFF4E5A68).copy(alpha = botAlpha / 255f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SkyLayer(theme: AuraTheme) {

    val isResumed by rememberIsResumed()

    // ── Solar elevation — updated every 30 seconds ─────────────────────────
    // Computed from TimeState.now() using the sunrise/sunset hours already
    // stored in AuraTheme (produced by SolarCalculator inside AuraEngine).
    // This gives continuous per-minute precision without duplicating any
    // existing solar calculation.
    var solarElev by remember {
        mutableFloatStateOf(currentSolarElevNorm(theme.sunriseHour, theme.sunsetHour))
    }

    LaunchedEffect(isResumed, theme.sunriseHour, theme.sunsetHour) {
        if (!isResumed) return@LaunchedEffect
        solarElev = currentSolarElevNorm(theme.sunriseHour, theme.sunsetHour)
        while (true) {
            delay(30_000L)
            solarElev = currentSolarElevNorm(theme.sunriseHour, theme.sunsetHour)
        }
    }

    // ── Moonlight contribution ──────────────────────────────────────────────
    // Lunar illumination from MoonPhaseCalculator — read once at composition,
    // same approach as StarLayer.  Value is stable for hours.
    val moonIllumination = remember {
        val phase = MoonPhaseCalculator.phaseFraction()
        ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()
    }

    val weather = theme.weatherState
    val season  = theme.profile.season

    // ── Continuous base atmosphere ──────────────────────────────────────────
    // Computed from solar elevation — NOT from discrete TimePhase
    val rawBase: Array<Color> = run {
        var stops = continuousBaseStops(solarElev)
        stops = applyMoonlightAtmosphere(stops, moonIllumination, solarElev)
        stops = applySeasonalModulation(stops, season, solarElev)
        stops
    }

    // Five independently animated base stops.
    // animateColorAsState is called exactly 5 times — never inside a loop.
    val b0 by animateColorAsState(rawBase[0], tween(3000), label = "sky0")
    val b1 by animateColorAsState(rawBase[1], tween(3000), label = "sky1")
    val b2 by animateColorAsState(rawBase[2], tween(3000), label = "sky2")
    val b3 by animateColorAsState(rawBase[3], tween(3000), label = "sky3")
    val b4 by animateColorAsState(rawBase[4], tween(3000), label = "sky4")

    // ── Weather overlay ─────────────────────────────────────────────────────
    val rawW = weatherOverlayStops(weather, solarElev)
    val transparent = Color.Transparent
    val w0 by animateColorAsState(rawW?.get(0) ?: transparent, tween(3000), label = "wsky0")
    val w1 by animateColorAsState(rawW?.get(1) ?: transparent, tween(3000), label = "wsky1")
    val w2 by animateColorAsState(rawW?.get(2) ?: transparent, tween(3000), label = "wsky2")
    val w3 by animateColorAsState(rawW?.get(3) ?: transparent, tween(3000), label = "wsky3")

    // ── Render ─────────────────────────────────────────────────────────────
    Canvas(modifier = Modifier.fillMaxSize()) {

        // Layer 1: continuous atmospheric sky
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to b0,
                    0.25f to b1,
                    0.55f to b2,
                    0.78f to b3,
                    1.00f to b4
                )
            )
        )

        // Layer 2: weather modulation overlay
        val anyWeather = w0 != transparent || w1 != transparent ||
                         w2 != transparent || w3 != transparent
        if (anyWeather) {
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to w0,
                        0.35f to w1,
                        0.72f to w2,
                        1.00f to w3
                    )
                )
            )
        }
    }
}
