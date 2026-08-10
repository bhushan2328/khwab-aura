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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SunStyle
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.sun.SunEngine
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

/**
 * Atmospheric solar rendering.
 *
 * ── What was removed ─────────────────────────────────────────────────────────
 *
 *  • 12 equally-spaced straight `drawLine` ray spokes with slow rotation.
 *  • The `rotate()` transform used to spin those spokes.
 *  • The `rayAngle` ticker coroutine.
 *  • The single ellipse atmospheric bloom (`drawOval` with one radial gradient).
 *  • The pulsing noon corona that used `sin(rayAngle * 3)`.
 *
 * These elements produced a cartoon sun-icon appearance.
 *
 * ── What replaced them ───────────────────────────────────────────────────────
 *
 *  • Four-layer atmospheric glow: concentric radial gradient circles at
 *    increasing radii, each at progressively lower opacity, with no visible
 *    boundary between them.  The layers read as:
 *      DISC       — bright compact solar disc (opaque centre, soft limb)
 *      LOCAL GLOW — immediate warm corona within ~2× disc radius
 *      BLOOM      — mid-range atmospheric scatter (~6× radius)
 *      HAZE       — wide very-faint atmospheric diffusion (~18× radius)
 *
 *  • Low-angle bloom stretch: at sunrise/sunset the outer two glow layers are
 *    drawn as horizontally stretched ovals to simulate the wider scatter
 *    horizon light travels through longer atmosphere — but the alpha fades to
 *    zero well before the geometric oval boundary so no visible ellipse edge.
 *
 *  • Weather visibility attenuation: the sun's alpha contribution scales with
 *    weather state (CLEAR=1.0, RAIN=0.45, FOG=0.35, STORM=0.18, OVERCAST=0.28)
 *    so the disc is visibly diffused and reduced under adverse conditions.
 *
 * ── Solar position ───────────────────────────────────────────────────────────
 *
 *  Position logic is fully preserved from the original:
 *    • isSolarAccurate → SunEngine.calculateSolarArc (real GPS data)
 *    • isLowAngle fallback → horizon y = 0.82
 *    • circle fallback → full-circle approximation
 *
 * ── Performance ──────────────────────────────────────────────────────────────
 *
 *  The 50ms ray-rotation ticker is removed.  Solar position is polled at
 *  30-second intervals as before.  No per-frame allocations are introduced;
 *  Brush objects are created per draw-call as in all Compose Canvas work.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Colour palette helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the primary solar disc colour for the given [SunStyle].
 *
 * Atmospheric solar colours:
 *   DAWN      — warm peach-orange (sun very low, longer atmosphere path)
 *   MORNING   — soft pale gold
 *   NOON      — near-white with faint warmth
 *   AFTERNOON — warm white/gold, subtly warmer than noon
 *   SUNSET    — warm amber-orange
 *   HIDDEN    — transparent
 *
 * All colours are deliberately desaturated relative to cartoon palettes —
 * the goal is warm atmospheric white, not neon orange or electric yellow.
 */
private fun solarDiscColor(style: SunStyle): Color = when (style) {
    SunStyle.DAWN      -> Color(0xFFEEA060)   // warm peach-orange, low angle
    SunStyle.MORNING   -> Color(0xFFFFE8A0)   // pale gold
    SunStyle.NOON      -> Color(0xFFFFFAE8)   // near-white, barely warm
    SunStyle.AFTERNOON -> Color(0xFFFFEFC0)   // warm white/gold
    SunStyle.SUNSET    -> Color(0xFFE8904A)   // warm amber-orange, low angle
    SunStyle.HIDDEN    -> Color.Transparent
}

/**
 * Returns the local corona/bloom colour for the given [SunStyle].
 *
 * The bloom is slightly more saturated than the disc colour but still
 * atmospheric — it represents warm air illuminated by the solar disc,
 * not a painted halo.
 */
private fun solarBloomColor(style: SunStyle): Color = when (style) {
    SunStyle.DAWN      -> Color(0xFFD07838)   // warm orange
    SunStyle.MORNING   -> Color(0xFFEED080)   // soft gold
    SunStyle.NOON      -> Color(0xFFF8EEC8)   // very pale warm
    SunStyle.AFTERNOON -> Color(0xFFEEDCA0)   // warm gold
    SunStyle.SUNSET    -> Color(0xFFCC6028)   // deeper orange-amber
    SunStyle.HIDDEN    -> Color.Transparent
}

/**
 * Returns a weather-driven visibility multiplier applied to all solar layers.
 *
 * CLEAR → full visibility.
 * CLOUDY → light diffusion, disc visible but softer.
 * OVERCAST → very diffused, disc faint.
 * RAIN → heavy diffusion, sun partially visible.
 * STORM → near-invisible, only faint haze remains.
 * FOG → fog diffuses solar disc strongly; bloom spreads.
 *
 * These are not binary on/off — partial visibility at all states except STORM
 * maintains the feel of daylight even under adverse weather.
 */
private fun weatherSolarVisibility(weather: WeatherState): Float = when (weather) {
    WeatherState.CLEAR  -> 1.00f
    WeatherState.CLOUDY -> 0.70f
    WeatherState.SNOW   -> 0.55f
    WeatherState.RAIN   -> 0.45f
    WeatherState.FOG    -> 0.35f
    WeatherState.STORM  -> 0.18f
}

/**
 * Under fog the bloom spreads significantly — fog diffuses the solar disc
 * into a wide hazy patch rather than a compact glow.
 */
private fun bloomSpreadMultiplier(weather: WeatherState): Float = when (weather) {
    WeatherState.FOG   -> 2.2f
    WeatherState.RAIN  -> 1.3f
    WeatherState.STORM -> 1.2f
    else               -> 1.0f
}

// ─────────────────────────────────────────────────────────────────────────────
// Atmospheric solar draw function
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws the complete atmospheric solar presentation at [center].
 *
 * Layer order (back to front):
 *   1. Wide atmospheric haze (18× disc, very faint)
 *   2. Mid scatter bloom (6× disc, low alpha, stretched horizontally at low angle)
 *   3. Local corona (2.2× disc, moderate alpha)
 *   4. Solar disc (compact, bright centre, soft limb)
 *
 * The haze and bloom layers at low angle use horizontally stretched ovals
 * to simulate the preferential horizontal scatter of near-horizon sunlight,
 * but their alpha falls to zero well before the geometric oval boundary so
 * no visible ellipse outline appears.
 *
 * @param center       Solar disc centre in canvas coordinates.
 * @param discRadius   Solar disc physical radius in pixels.
 * @param discColor    Primary disc colour (from [solarDiscColor]).
 * @param bloomColor   Bloom/corona colour (from [solarBloomColor]).
 * @param visibility   0..1 weather attenuation (from [weatherSolarVisibility]).
 * @param bloomSpread  Bloom radius multiplier (from [bloomSpreadMultiplier]).
 * @param isLowAngle   True at DAWN/SUNSET — enables horizontal scatter stretch.
 */
private fun DrawScope.drawAtmosphericSun(
    center:      Offset,
    discRadius:  Float,
    discColor:   Color,
    bloomColor:  Color,
    visibility:  Float,
    bloomSpread: Float,
    isLowAngle:  Boolean
) {
    // ── Layer 1: Wide atmospheric haze ────────────────────────────────────────
    // Very large radius, extremely faint — represents the broader atmospheric
    // scatter seen as a subtle brightening of the sky around the sun.
    // At low angle, stretched into an oval to simulate horizon-spread scatter.
    val hazeRadius = discRadius * 18f * bloomSpread
    val hazeAlpha  = 0.07f * visibility

    if (isLowAngle) {
        // Horizontal stretch: 2.2× wide, 0.55× tall at the haze scale
        val hw = hazeRadius * 2.2f
        val hh = hazeRadius * 0.55f
        drawOval(
            brush   = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to bloomColor.copy(alpha = hazeAlpha),
                    0.55f to bloomColor.copy(alpha = hazeAlpha * 0.30f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = hw * 0.50f
            ),
            topLeft = Offset(center.x - hw, center.y - hh),
            size    = Size(hw * 2f, hh * 2f)
        )
    } else {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to bloomColor.copy(alpha = hazeAlpha),
                    0.50f to bloomColor.copy(alpha = hazeAlpha * 0.35f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = hazeRadius
            ),
            radius = hazeRadius,
            center = center
        )
    }

    // ── Layer 2: Mid scatter bloom ────────────────────────────────────────────
    // The most visually significant glow layer. Represents the immediate
    // atmospheric scatter visible as a bright warm region around the sun.
    // Stronger at low angle (longer atmospheric path → more scattering).
    val bloomRadius = discRadius * 6f * bloomSpread
    val bloomAlpha  = if (isLowAngle) 0.30f * visibility else 0.18f * visibility

    if (isLowAngle) {
        val bw = bloomRadius * 1.80f
        val bh = bloomRadius * 0.60f
        drawOval(
            brush   = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to bloomColor.copy(alpha = bloomAlpha),
                    0.40f to bloomColor.copy(alpha = bloomAlpha * 0.45f),
                    0.75f to bloomColor.copy(alpha = bloomAlpha * 0.12f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = bw * 0.50f
            ),
            topLeft = Offset(center.x - bw, center.y - bh),
            size    = Size(bw * 2f, bh * 2f)
        )
    } else {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to bloomColor.copy(alpha = bloomAlpha),
                    0.35f to bloomColor.copy(alpha = bloomAlpha * 0.50f),
                    0.70f to bloomColor.copy(alpha = bloomAlpha * 0.14f),
                    1.00f to Color.Transparent
                ),
                center = center,
                radius = bloomRadius
            ),
            radius = bloomRadius,
            center = center
        )
    }

    // ── Layer 3: Local corona ─────────────────────────────────────────────────
    // Tight immediate glow around the disc — the innermost halo.
    // Always circular; no horizontal stretch since the disc is effectively a
    // point source at this scale.
    val coronaRadius = discRadius * 2.4f
    val coronaAlpha  = 0.42f * visibility
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to discColor.copy(alpha = coronaAlpha),
                0.45f to discColor.copy(alpha = coronaAlpha * 0.40f),
                0.80f to bloomColor.copy(alpha = coronaAlpha * 0.12f),
                1.00f to Color.Transparent
            ),
            center = center,
            radius = coronaRadius
        ),
        radius = coronaRadius,
        center = center
    )

    // ── Layer 4: Solar disc ───────────────────────────────────────────────────
    // Compact bright disc with soft limb.  The centre is near-white (real solar
    // disc centre is very bright), transitioning to the atmospheric disc colour
    // and dissolving to transparent at the limb.  No hard edge.
    //
    // The 3-stop gradient:
    //   centre (0%)   → near-white  (bright solar surface)
    //   mid    (55%)  → discColor   (atmospheric colouration at limb)
    //   edge   (100%) → transparent (soft limb dissolution)
    //
    // The centre white is capped at 0.90 opacity so the disc doesn't become
    // a uniform white circle — real solar discs have structure even through
    // atmosphere.
    val discAlpha = (0.90f * visibility).coerceIn(0f, 0.90f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = discAlpha),
                0.55f to discColor.copy(alpha = discAlpha * 0.85f),
                1.00f to discColor.copy(alpha = 0f)
            ),
            center = center,
            radius = discRadius
        ),
        radius = discRadius,
        center = center
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SunLayer(theme: AuraTheme) {

    if (theme.profile.sun == SunStyle.HIDDEN) return

    val isResumed by rememberIsResumed()

    // SunEngine is stateless — create once, reuse forever
    val engine = remember { SunEngine() }

    val sunriseHour = theme.sunriseHour
    val sunsetHour  = theme.sunsetHour

    var position by remember {
        mutableStateOf(engine.calculate(TimeState.now(), sunriseHour, sunsetHour))
    }

    // Solar position update — 30-second poll to track the sun arc.
    // The 50ms ray-rotation ticker from the old implementation is removed
    // since there are no longer any rotating elements.
    // isActive guard added so the coroutine exits cleanly when the LaunchedEffect
    // is cancelled (e.g. lifecycle pause or composable leaving composition).
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        position = engine.calculate(TimeState.now(), sunriseHour, sunsetHour)
        while (isActive) {
            delay(30_000L)
            position = engine.calculate(TimeState.now(), sunriseHour, sunsetHour)
        }
    }

    val sunStyle = theme.profile.sun
    val discColor  = solarDiscColor(sunStyle)
    val bloomColor = solarBloomColor(sunStyle)

    val isLowAngle = sunStyle == SunStyle.DAWN || sunStyle == SunStyle.SUNSET

    // Weather visibility modulates the entire solar presentation
    val visibility  = weatherSolarVisibility(theme.weatherState)
    val bloomSpread = bloomSpreadMultiplier(theme.weatherState)

    Canvas(modifier = Modifier.fillMaxSize()) {

        // Disc physical radius — slightly smaller than the old 0.08 which read
        // as an oversized ball.  0.048 maps to ~21dp on a 440dp-wide phone,
        // matching the apparent visual size of the sun in real sky photography.
        val discRadius = size.minDimension * 0.048f

        // ── Solar position ──────────────────────────────────────────────────
        // Three-branch logic preserved exactly from the original SunLayer.
        val center = if (theme.isSolarAccurate) {
            Offset(
                x = size.width  * position.x,
                y = size.height * position.y
            )
        } else if (isLowAngle) {
            Offset(
                x = size.width  * position.x,
                y = size.height * 0.82f
            )
        } else {
            Offset(
                x = size.width  * position.x,
                y = size.height * (1f - position.y) * 0.55f
            )
        }

        // ── Draw atmospheric sun ────────────────────────────────────────────
        drawAtmosphericSun(
            center      = center,
            discRadius  = discRadius,
            discColor   = discColor,
            bloomColor  = bloomColor,
            visibility  = visibility,
            bloomSpread = bloomSpread,
            isLowAngle  = isLowAngle
        )
    }
}
