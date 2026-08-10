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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.model.WeatherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// SEASON LAYER — Realistic seasonal atmospheric ambience
//
// Architecture rules:
//   - Existing seasonal state / season enum / time/weather states unchanged.
//   - rememberIsResumed() + animationsEnabled gated.
//   - All particle data built once with remember; updated in-place.
//   - Single shared animation coroutine per active effect.
//   - LocalWindIntensity consumed from AnimationLayer.
//   - No new engines, no new architecture.
// ─────────────────────────────────────────────────────────────────────────────

private val TWO_PI_F = (2.0 * PI).toFloat()

// ─────────────────────────────────────────────────────────────────────────────
// Shared depth enum
// ─────────────────────────────────────────────────────────────────────────────

private enum class ParticleDepth { FAR, MID, NEAR }

// ─────────────────────────────────────────────────────────────────────────────
// Weather / time visibility multipliers
// ─────────────────────────────────────────────────────────────────────────────

/** Reduce seasonal particle visibility in bad weather. */
private fun weatherVisibility(weather: WeatherState): Float = when (weather) {
    WeatherState.CLEAR  -> 1.00f
    WeatherState.CLOUDY -> 0.80f
    WeatherState.SNOW   -> 0.55f
    WeatherState.RAIN   -> 0.45f
    WeatherState.FOG    -> 0.40f
    WeatherState.STORM  -> 0.20f
}

/**
 * Time-of-day atmospheric visibility for seasonal particles,
 * derived continuously from normalised solar elevation.
 *
 * Full visibility at noon (solarElev ≈ +1), fades toward zero at deep night
 * (solarElev ≈ -1). The curve concentrates most of the fade in the twilight
 * band so daytime particles remain visible across the whole day arc.
 *
 * Replaces the discrete TimePhase lookup (which caused a step each time the
 * phase changed) with a continuous mapping from [AuraTheme.solarElevNorm].
 */
private fun timeVisibility(solarElev: Float): Float = when {
    solarElev >= 0.10f  -> 1.00f  // full daytime — particles clearly visible
    solarElev >= 0.05f  -> 0.90f  // sunrise/sunset zone just above horizon
    solarElev >= -0.05f -> 0.75f  // horizon crossing
    solarElev >= -0.15f -> 0.55f  // civil twilight
    solarElev >= -0.30f -> 0.35f  // evening / pre-dawn
    solarElev >= -0.55f -> 0.22f  // deep evening
    else                -> 0.15f  // astronomical night
}

/**
 * Smooth horizon attenuation for seasonal particles.
 *
 * Particles near the lower portion of the canvas are affected by atmospheric
 * haze — the further from the viewer's perceived upper-sky focus, the less
 * contrast they retain.  FAR depth particles lose visibility earlier.
 *
 * [y]     normalised canvas Y of the particle (0 = top, 1 = bottom).
 * [depth] particle depth tier.
 *
 * Returns a multiplier in [0.35, 1.0].
 */
private fun seasonHorizonFactor(y: Float, depth: ParticleDepth): Float {
    val fadeStart = when (depth) {
        ParticleDepth.FAR  -> 0.72f   // distant particles fade earlier
        ParticleDepth.MID  -> 0.80f
        ParticleDepth.NEAR -> 0.90f   // foreground particles stay visible longer
    }
    val fadeEnd = when (depth) {
        ParticleDepth.FAR  -> 0.90f
        ParticleDepth.MID  -> 0.96f
        ParticleDepth.NEAR -> 1.00f
    }
    return when {
        y < fadeStart -> 1.0f
        y < fadeEnd   -> 1f - ((y - fadeStart) / (fadeEnd - fadeStart))
                             .coerceIn(0f, 1f) * 0.65f
        else          -> 0.35f
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PETAL — spring cherry/sakura
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One spring petal.
 *
 * Organic variation:
 *  [halfW] / [halfH]: individual width and height so petals are not identical.
 *  [taper]: makes one end narrower than the other (like a real petal).
 *  [sway]: independent phase for sinusoidal lateral drift.
 *  [swayAmp]: per-petal horizontal wobble amplitude.
 *  [depth]: controls size, alpha, speed.
 *  [color]: slightly varied between pink-white tones.
 */
private class Petal(
    var  x:          Float,
    var  y:          Float,
    val  fallSpeed:  Float,   // normalised units/tick
    val  driftBase:  Float,   // base horizontal drift/tick
    var  sway:       Float,   // sway phase accumulator
    val  swayRate:   Float,   // sway phase speed/tick
    val  swayAmp:    Float,   // horizontal sway amplitude (px)
    var  spin:       Float,   // tumble rotation (radians)
    val  spinRate:   Float,   // rotation speed/tick
    val  halfW:      Float,   // petal half-width (px)
    val  halfH:      Float,   // petal half-height (px)
    val  taper:      Float,   // 0..1, tip width fraction (1 = symmetric oval)
    val  depth:      ParticleDepth,
    val  color:      Color,
    val  baseAlpha:  Float
)

private val PETAL_COLORS = arrayOf(
    Color(0xFFFFD6E8),   // very pale pink
    Color(0xFFFAC0D5),   // soft pink
    Color(0xFFFFEEF4),   // almost white with pink blush
    Color(0xFFF5E0E8),   // muted dusty rose-white
    Color(0xFFFFDDE8),   // light blush
)

private fun buildPetals(rng: Random): List<Petal> = buildList {
    repeat(28) {
        val roll = rng.nextFloat()
        val depth = when {
            roll < 0.55f -> ParticleDepth.FAR
            roll < 0.85f -> ParticleDepth.MID
            else         -> ParticleDepth.NEAR
        }
        val depthScale = when (depth) {
            ParticleDepth.FAR  -> 0.55f
            ParticleDepth.MID  -> 0.80f
            ParticleDepth.NEAR -> 1.00f
        }
        val baseW = (3.0f + rng.nextFloat() * 4.0f) * depthScale
        val baseH = (1.8f + rng.nextFloat() * 2.2f) * depthScale
        val alphaBase = when (depth) {
            ParticleDepth.FAR  -> 0.18f + rng.nextFloat() * 0.14f
            ParticleDepth.MID  -> 0.35f + rng.nextFloat() * 0.20f
            ParticleDepth.NEAR -> 0.50f + rng.nextFloat() * 0.20f
        }
        val speed = when (depth) {
            ParticleDepth.FAR  -> 0.00040f + rng.nextFloat() * 0.00030f
            ParticleDepth.MID  -> 0.00060f + rng.nextFloat() * 0.00040f
            ParticleDepth.NEAR -> 0.00090f + rng.nextFloat() * 0.00060f
        }
        add(Petal(
            x          = rng.nextFloat(),
            y          = rng.nextFloat(),
            fallSpeed  = speed,
            driftBase  = (rng.nextFloat() - 0.5f) * 0.00060f,
            sway       = rng.nextFloat() * TWO_PI_F,
            swayRate   = 0.018f + rng.nextFloat() * 0.020f,
            swayAmp    = (4f + rng.nextFloat() * 8f) * depthScale,
            spin       = rng.nextFloat() * TWO_PI_F,
            spinRate   = (rng.nextFloat() - 0.5f) * 0.030f,
            halfW      = baseW,
            halfH      = baseH,
            taper      = 0.50f + rng.nextFloat() * 0.45f,   // 0.50..0.95 — slight taper
            depth      = depth,
            color      = PETAL_COLORS[rng.nextInt(PETAL_COLORS.size)],
            baseAlpha  = alphaBase
        ))
    }
}

/** Draws one petal as a tapered oval path. */
private fun DrawScope.drawPetal(petal: Petal, alpha: Float) {
    val cx = petal.x * size.width + sin(petal.sway) * petal.swayAmp
    val cy = petal.y * size.height
    val w  = petal.halfW
    val h  = petal.halfH
    val color = petal.color.copy(alpha = alpha)

    withTransform({
        translate(cx, cy)
        rotate(Math.toDegrees(petal.spin.toDouble()).toFloat())
    }) {
        // Tapered oval: tip end narrower than base
        // We approximate with a simple oval — the taper is achieved by
        // drawing the oval at reduced width on one end using an offset ellipse.
        // For simplicity and performance: drawOval for most petals,
        // with a slight offset on the narrow end via two stacked ovals of
        // different alphas to create the tapered impression.
        drawOval(
            color   = color,
            topLeft = Offset(-w, -h),
            size    = androidx.compose.ui.geometry.Size(w * 2f, h * 2f)
        )
        // Thin inner highlight — makes petal feel translucent not flat
        drawOval(
            color   = color.copy(alpha = alpha * 0.30f),
            topLeft = Offset(-w * 0.55f, -h * 0.40f),
            size    = androidx.compose.ui.geometry.Size(w * 1.1f, h * 0.80f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEAF — autumn
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One autumn leaf.
 *
 * Key difference from petals: leaves use a distinct asymmetric leaf-shaped
 * Path rather than an oval, giving them a different silhouette.
 *
 * [wobble]: controls sinusoidal lateral drift (leaf catches air).
 * [wobbleAmp]: per-leaf horizontal flutter (different from petals).
 * [spinRate]: tumbling — leaves tumble faster than petals.
 * [color]: muted amber/brown/rusty palette.
 * [aspect]: length-to-width ratio varies per leaf.
 */
private class Leaf(
    var  x:         Float,
    var  y:         Float,
    val  fallSpeed: Float,
    val  driftBase: Float,
    var  wobble:    Float,
    val  wobbleRate:Float,
    val  wobbleAmp: Float,
    var  spin:      Float,
    val  spinRate:  Float,
    val  halfLen:   Float,   // leaf half-length
    val  halfWid:   Float,   // leaf half-width (aspect varies)
    val  curve:     Float,   // curvature control point offset fraction 0.2..0.5
    val  depth:     ParticleDepth,
    val  color:     Color,
    val  baseAlpha: Float
)

/** Muted natural autumn leaf colours — no saturated Material orange. */
private val LEAF_COLORS = arrayOf(
    Color(0xFF8B5E2A),   // muted amber-brown
    Color(0xFF9E4E1E),   // rusty orange
    Color(0xFF7A4820),   // dark brown-orange
    Color(0xFFA06030),   // warm amber
    Color(0xFF6B4520),   // deep brown
    Color(0xFF8C3A18),   // dark rusty red-brown
    Color(0xFF7E5835),   // golden-brown
    Color(0xFF955025),   // burnt orange-brown
)

private fun buildLeaves(rng: Random): List<Leaf> = buildList {
    repeat(26) {
        val roll = rng.nextFloat()
        val depth = when {
            roll < 0.50f -> ParticleDepth.FAR
            roll < 0.82f -> ParticleDepth.MID
            else         -> ParticleDepth.NEAR
        }
        val depthScale = when (depth) {
            ParticleDepth.FAR  -> 0.50f
            ParticleDepth.MID  -> 0.75f
            ParticleDepth.NEAR -> 1.00f
        }
        val halfLen = (4.5f + rng.nextFloat() * 5.0f) * depthScale
        // Aspect ratio varies — some narrow, some rounder
        val aspect  = 0.35f + rng.nextFloat() * 0.35f
        val halfWid = halfLen * aspect
        val alphaBase = when (depth) {
            ParticleDepth.FAR  -> 0.20f + rng.nextFloat() * 0.15f
            ParticleDepth.MID  -> 0.38f + rng.nextFloat() * 0.20f
            ParticleDepth.NEAR -> 0.55f + rng.nextFloat() * 0.20f
        }
        val speed = when (depth) {
            ParticleDepth.FAR  -> 0.00045f + rng.nextFloat() * 0.00030f
            ParticleDepth.MID  -> 0.00070f + rng.nextFloat() * 0.00045f
            ParticleDepth.NEAR -> 0.00100f + rng.nextFloat() * 0.00065f
        }
        add(Leaf(
            x          = rng.nextFloat(),
            y          = rng.nextFloat(),
            fallSpeed  = speed,
            driftBase  = (rng.nextFloat() - 0.5f) * 0.00080f,
            wobble     = rng.nextFloat() * TWO_PI_F,
            wobbleRate = 0.020f + rng.nextFloat() * 0.022f,
            wobbleAmp  = (5f + rng.nextFloat() * 10f) * depthScale,
            spin       = rng.nextFloat() * TWO_PI_F,
            spinRate   = (rng.nextFloat() - 0.5f) * 0.048f,   // leaves tumble faster
            halfLen    = halfLen,
            halfWid    = halfWid,
            curve      = 0.25f + rng.nextFloat() * 0.22f,
            depth      = depth,
            color      = LEAF_COLORS[rng.nextInt(LEAF_COLORS.size)],
            baseAlpha  = alphaBase
        ))
    }
}

/**
 * Draws one autumn leaf using an asymmetric leaf-shaped path.
 *
 * The leaf is approximated with two cubic Bézier curves that meet at the tip
 * and base, creating a pointed-oval silhouette distinct from the circular
 * petal shape.
 */
private fun DrawScope.drawLeaf(leaf: Leaf, alpha: Float) {
    val cx = leaf.x * size.width + sin(leaf.wobble) * leaf.wobbleAmp
    val cy = leaf.y * size.height
    val len = leaf.halfLen
    val wid = leaf.halfWid
    val c   = leaf.curve * len    // control point X offset
    val color = leaf.color.copy(alpha = alpha)

    withTransform({
        translate(cx, cy)
        rotate(Math.toDegrees(leaf.spin.toDouble()).toFloat())
    }) {
        // Leaf path: base at (-len,0) → tip at (+len,0)
        // Upper and lower edges curve outward to ±wid at the mid-point
        val path = Path().apply {
            moveTo(-len, 0f)
            cubicTo(
                -len + c, -wid,         // control 1: near base, top
                 len - c, -wid,         // control 2: near tip, top
                 len, 0f                // tip
            )
            cubicTo(
                 len - c, wid,          // control 1: near tip, bottom
                -len + c, wid,          // control 2: near base, bottom
                -len, 0f               // back to base
            )
            close()
        }
        drawPath(path = path, color = color)

        // Subtle midrib line — very low alpha, gives the leaf a little structure
        drawLine(
            color       = color.copy(alpha = alpha * 0.25f),
            start       = Offset(-len * 0.85f, 0f),
            end         = Offset(len * 0.85f, 0f),
            strokeWidth = 0.8f,
            cap         = StrokeCap.Round
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// POLLEN — summer airborne particles
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One pollen / dandelion seed.
 *
 * Rendered as a single tiny circle at very low alpha — no glowing core,
 * no bright halo.  The effect should barely be visible: just a hint of
 * particles in warm summer air.
 */
private class PollenBit(
    var  x:         Float,
    var  y:         Float,
    val  riseSpeed: Float,
    val  drift:     Float,
    var  wobble:    Float,
    val  wobbleRate:Float,
    val  wobbleAmp: Float,
    val  radius:    Float,
    val  baseAlpha: Float,
    val  depth:     ParticleDepth
)

private fun buildPollen(rng: Random): List<PollenBit> = buildList {
    repeat(10) {
        val depth = if (rng.nextFloat() < 0.60f) ParticleDepth.FAR else ParticleDepth.MID
        val depthScale = if (depth == ParticleDepth.FAR) 0.65f else 1.00f
        add(PollenBit(
            x          = rng.nextFloat(),
            y          = rng.nextFloat(),
            riseSpeed  = 0.00025f + rng.nextFloat() * 0.00035f,
            drift      = (rng.nextFloat() - 0.5f) * 0.00030f,
            wobble     = rng.nextFloat() * TWO_PI_F,
            wobbleRate = 0.015f + rng.nextFloat() * 0.012f,
            wobbleAmp  = (2f + rng.nextFloat() * 4f) * depthScale,
            radius     = (1.2f + rng.nextFloat() * 1.4f) * depthScale,
            baseAlpha  = 0.15f + rng.nextFloat() * 0.18f,   // very low alpha — barely visible
            depth      = depth
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FIREFLY — summer night
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One firefly with realistic irregular bioluminescent behaviour.
 *
 * Real fireflies have a distinct flash pattern: dim → brighter → peak →
 * fade → dark pause → repeat.  Each firefly has different timing.
 *
 * [glowPhase] accumulates phase for the illumination cycle.
 * [glowCycleLen] is the full cycle length in ticks (dark+light).
 * [glowOnFrac] fraction of cycle spent glowing (vs dark).
 * [peakAlpha] individual peak brightness — no two fireflies identical.
 * [driftX]/[driftY]: very slow independent drift velocity.
 */
private class Firefly(
    var x:           Float,
    var y:           Float,
    var glowPhase:   Float,   // 0..glowCycleLen
    val glowCycleLen:Float,   // ticks for full on+off cycle
    val glowOnFrac:  Float,   // fraction of cycle actively glowing
    val peakAlpha:   Float,   // individual peak alpha
    val radius:      Float,   // glow radius
    val color:       Color,   // warm bioluminescent yellow-green
    val driftX:      Float,   // horizontal drift/tick
    val driftY:      Float,   // vertical drift/tick
    var dirTimer:    Float,   // countdown to direction change
    val depth:       ParticleDepth
)

/** Natural warm bioluminescent firefly colours — no neon green. */
private val FIREFLY_COLORS = arrayOf(
    Color(0xFFB8C44A),   // muted yellow-green
    Color(0xFFC4B83A),   // warm yellow
    Color(0xFFAAB835),   // soft olive-yellow
    Color(0xFFBFC040),   // yellow-lime, desaturated
)

private fun buildFireflies(rng: Random): List<Firefly> = buildList {
    repeat(20) {
        val depth = when (rng.nextFloat()) {
            in 0f..0.45f -> ParticleDepth.FAR
            in 0f..0.80f -> ParticleDepth.MID
            else         -> ParticleDepth.NEAR
        }
        val depthScale = when (depth) {
            ParticleDepth.FAR  -> 0.55f
            ParticleDepth.MID  -> 0.80f
            ParticleDepth.NEAR -> 1.00f
        }
        // Irregular cycle: 80–220 ticks total; 20–50% of that actively glowing
        val cycleLen = 80f + rng.nextFloat() * 140f
        val onFrac   = 0.20f + rng.nextFloat() * 0.30f
        val peakAlpha = (0.40f + rng.nextFloat() * 0.35f) * depthScale
        val radius    = (2.5f + rng.nextFloat() * 2.5f) * depthScale

        add(Firefly(
            x            = rng.nextFloat(),
            y            = 0.35f + rng.nextFloat() * 0.55f,   // lower half of sky
            glowPhase    = rng.nextFloat() * cycleLen,        // start at random phase
            glowCycleLen = cycleLen,
            glowOnFrac   = onFrac,
            peakAlpha    = peakAlpha,
            radius       = radius,
            color        = FIREFLY_COLORS[rng.nextInt(FIREFLY_COLORS.size)],
            driftX       = (rng.nextFloat() - 0.5f) * 0.0010f,
            driftY       = (rng.nextFloat() - 0.5f) * 0.0006f,
            dirTimer     = 30f + rng.nextFloat() * 60f,
            depth        = depth
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WINTER CRYSTAL
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One winter frost / ice crystal particle.
 *
 * These are subtle atmospheric particles, not decorative snowflake stickers.
 * Most are near-invisible; a few are slightly more visible.
 * [armCount] is 2 or 3 — draws 4 or 6 tiny lines.
 * [size] is genuinely small (2–7 px radius).
 */
private class FrostCrystal(
    var  x:        Float,
    var  y:        Float,
    val  speed:    Float,
    val  drift:    Float,
    val  size:     Float,
    var  spin:     Float,
    val  spinRate: Float,
    val  armCount: Int,    // 2 or 3
    val  depth:    ParticleDepth,
    val  baseAlpha:Float
)

private fun buildFrostCrystals(rng: Random): List<FrostCrystal> = buildList {
    repeat(32) {
        val depth = when (rng.nextFloat()) {
            in 0f..0.55f -> ParticleDepth.FAR
            in 0f..0.82f -> ParticleDepth.MID
            else         -> ParticleDepth.NEAR
        }
        val depthScale = when (depth) {
            ParticleDepth.FAR  -> 0.55f
            ParticleDepth.MID  -> 0.80f
            ParticleDepth.NEAR -> 1.00f
        }
        val alphaBase = when (depth) {
            ParticleDepth.FAR  -> 0.12f + rng.nextFloat() * 0.12f
            ParticleDepth.MID  -> 0.25f + rng.nextFloat() * 0.18f
            ParticleDepth.NEAR -> 0.40f + rng.nextFloat() * 0.20f
        }
        add(FrostCrystal(
            x          = rng.nextFloat(),
            y          = rng.nextFloat(),
            speed      = (0.00035f + rng.nextFloat() * 0.00045f),
            drift      = (rng.nextFloat() - 0.5f) * 0.00050f,
            size       = (2.0f + rng.nextFloat() * 4.5f) * depthScale,
            spin       = rng.nextFloat() * TWO_PI_F,
            spinRate   = (rng.nextFloat() - 0.5f) * 0.025f,
            armCount   = if (rng.nextBoolean()) 2 else 3,
            depth      = depth,
            baseAlpha  = alphaBase
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas draw helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawFrostCrystal(c: FrostCrystal, alpha: Float) {
    val cx = c.x * size.width
    val cy = c.y * size.height
    val r  = c.size
    val color = Color(0xFFF0F4F8).copy(alpha = alpha)
    withTransform({
        translate(cx, cy)
        rotate(Math.toDegrees(c.spin.toDouble()).toFloat())
    }) {
        for (i in 0 until c.armCount) {
            val angle = i * (PI.toFloat() / c.armCount.toFloat())
            val ax = cos(angle) * r
            val ay = sin(angle) * r
            drawLine(
                color       = color,
                start       = Offset(-ax, -ay),
                end         = Offset(ax, ay),
                strokeWidth = 0.9f,
                cap         = StrokeCap.Round
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP-LEVEL COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SeasonLayer(theme: AuraTheme) {

    val season    = theme.profile.season
    val solarElev = theme.solarElevNorm

    // Use continuous solar elevation for night/daytime classification.
    // Avoids discrete TimePhase steps; transitions match SkyLayer and CloudLayer.
    val isNight   = solarElev <= -0.08f    // sun clearly below horizon
    val isDaytime = solarElev >= 0.10f     // sun clearly above horizon

    val isClear = theme.weatherState == WeatherState.CLEAR ||
            theme.weatherState == WeatherState.CLOUDY

    val isResumed       by rememberIsResumed()
    val windIntensity    = LocalWindIntensity.current
    val animationsEnabled= theme.animationsEnabled
    val wVis             = weatherVisibility(theme.weatherState)
    val tVis             = timeVisibility(solarElev)
    val masterVis        = wVis * tVis

    when {
        season == Season.SPRING && !isNight ->
            SpringPetals(
                isResumed          = isResumed,
                windIntensity      = windIntensity,
                animationsEnabled  = animationsEnabled,
                masterVis          = masterVis
            )

        season == Season.AUTUMN && !isNight ->
            AutumnLeaves(
                isResumed          = isResumed,
                windIntensity      = windIntensity,
                animationsEnabled  = animationsEnabled,
                masterVis          = masterVis
            )

        season == Season.SUMMER && isDaytime && isClear ->
            SummerPollen(
                isResumed          = isResumed,
                windIntensity      = windIntensity,
                animationsEnabled  = animationsEnabled,
                masterVis          = masterVis
            )

        season == Season.SUMMER && isNight ->
            Fireflies(
                isResumed          = isResumed,
                animationsEnabled  = animationsEnabled,
                masterVis          = masterVis
            )

        season == Season.WINTER ->
            WinterFrost(
                isResumed          = isResumed,
                windIntensity      = windIntensity,
                animationsEnabled  = animationsEnabled,
                masterVis          = masterVis
            )

        else -> Unit
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SPRING PETALS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpringPetals(
    isResumed:         Boolean,
    windIntensity:     Float,
    animationsEnabled: Boolean,
    masterVis:         Float
) {
    val petals = remember {
        mutableStateListOf<Petal>().also { list ->
            list.addAll(buildPetals(Random))
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val windDrift = windIntensity * 0.00065f
            for (p in petals) {
                p.y    += p.fallSpeed
                p.x    += p.driftBase + windDrift
                p.sway += p.swayRate + windIntensity * 0.008f
                p.spin += p.spinRate * (1f + windIntensity * 1.2f)
                if (p.y > 1.05f) { p.y = -0.04f; p.x = Random.nextFloat() }
                if (p.x > 1.05f) p.x = -0.03f
                if (p.x < -0.05f) p.x = 1.03f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (p in petals) {
            val alpha = (p.baseAlpha * masterVis * seasonHorizonFactor(p.y, p.depth))
                .coerceIn(0f, 1f)
            if (alpha > 0.01f) drawPetal(p, alpha)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AUTUMN LEAVES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AutumnLeaves(
    isResumed:         Boolean,
    windIntensity:     Float,
    animationsEnabled: Boolean,
    masterVis:         Float
) {
    val leaves = remember {
        mutableStateListOf<Leaf>().also { list ->
            list.addAll(buildLeaves(Random))
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val windDrift = windIntensity * 0.00080f
            for (l in leaves) {
                l.y       += l.fallSpeed
                l.x       += l.driftBase + windDrift
                l.wobble  += l.wobbleRate + windIntensity * 0.010f
                l.spin    += l.spinRate * (1f + windIntensity * 1.5f)
                if (l.y > 1.05f) { l.y = -0.04f; l.x = Random.nextFloat() }
                if (l.x > 1.05f) l.x = -0.03f
                if (l.x < -0.05f) l.x = 1.03f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (l in leaves) {
            val alpha = (l.baseAlpha * masterVis * seasonHorizonFactor(l.y, l.depth))
                .coerceIn(0f, 1f)
            if (alpha > 0.01f) drawLeaf(l, alpha)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUMMER POLLEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummerPollen(
    isResumed:         Boolean,
    windIntensity:     Float,
    animationsEnabled: Boolean,
    masterVis:         Float
) {
    val bits = remember {
        mutableStateListOf<PollenBit>().also { list ->
            list.addAll(buildPollen(Random))
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val windDrift = windIntensity * 0.00040f
            for (b in bits) {
                b.y      -= b.riseSpeed
                b.x      += b.drift + windDrift
                b.wobble += b.wobbleRate
                if (b.y < -0.05f) { b.y = 1.05f; b.x = Random.nextFloat() }
                if (b.x > 1.05f) b.x = -0.03f
                if (b.x < -0.05f) b.x = 1.03f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (b in bits) {
            val alpha = (b.baseAlpha * masterVis * seasonHorizonFactor(b.y, b.depth))
                .coerceIn(0f, 1f)
            if (alpha > 0.005f) {
                val cx = b.x * size.width + sin(b.wobble) * b.wobbleAmp
                val cy = b.y * size.height
                // Single soft dot — warm near-white, very faint
                // NO glowing core, NO bright white inner circle
                drawCircle(
                    color  = Color(0xFFFAF5E0).copy(alpha = alpha),
                    radius = b.radius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FIREFLIES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Fireflies(
    isResumed:         Boolean,
    animationsEnabled: Boolean,
    masterVis:         Float
) {
    val flies = remember {
        mutableStateListOf<Firefly>().also { list ->
            list.addAll(buildFireflies(Random))
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (fly in flies) {
                // Advance glow phase
                fly.glowPhase = (fly.glowPhase + 1f) % fly.glowCycleLen

                // Slow irregular drift
                fly.x = (fly.x + fly.driftX).coerceIn(0.02f, 0.98f)
                fly.y = (fly.y + fly.driftY).coerceIn(0.20f, 0.95f)

                // Direction timer — occasional tiny direction nudge
                fly.dirTimer -= 1f
                if (fly.dirTimer <= 0f) {
                    // nudge position slightly; driftX/driftY are val, no change needed
                    fly.dirTimer = 40f + Random.nextFloat() * 80f
                }
            }
            delay(60L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (fly in flies) {
            // Compute glow alpha from phase position within cycle
            val glowOnTicks  = fly.glowCycleLen * fly.glowOnFrac
            val currentAlpha: Float

            if (fly.glowPhase < glowOnTicks) {
                // Within the glow window — use a smooth bell-curve
                // that rises and falls: sin(π·t / glowOnTicks)
                val t = fly.glowPhase / glowOnTicks   // 0..1
                val bellCurve = sin(PI.toFloat() * t) // 0→1→0
                currentAlpha = fly.peakAlpha * bellCurve
            } else {
                // Dark pause — firefly is completely off
                currentAlpha = 0f
            }

            val alpha = (currentAlpha * masterVis).coerceIn(0f, 1f)
            if (alpha > 0.01f) {
                val cx = fly.x * size.width
                val cy = fly.y * size.height

                // Soft outer glow
                drawCircle(
                    color  = fly.color.copy(alpha = alpha * 0.40f),
                    radius = fly.radius * 2.2f,
                    center = Offset(cx, cy)
                )
                // Core point
                drawCircle(
                    color  = fly.color.copy(alpha = alpha),
                    radius = fly.radius,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WINTER FROST
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WinterFrost(
    isResumed:         Boolean,
    windIntensity:     Float,
    animationsEnabled: Boolean,
    masterVis:         Float
) {
    val crystals = remember {
        mutableStateListOf<FrostCrystal>().also { list ->
            list.addAll(buildFrostCrystals(Random))
        }
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val windDrift = windIntensity * 0.00035f
            for (c in crystals) {
                c.y    += c.speed
                c.x    += c.drift + windDrift
                c.spin += c.spinRate * (1f + windIntensity * 0.8f)
                if (c.y > 1.05f) { c.y = -0.03f; c.x = Random.nextFloat() }
                if (c.x > 1.05f) c.x = -0.03f
                if (c.x < -0.05f) c.x = 1.03f
            }
            delay(30L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (c in crystals) {
            val alpha = (c.baseAlpha * masterVis * seasonHorizonFactor(c.y, c.depth))
                .coerceIn(0f, 1f)
            if (alpha > 0.01f) drawFrostCrystal(c, alpha)
        }
    }
}
