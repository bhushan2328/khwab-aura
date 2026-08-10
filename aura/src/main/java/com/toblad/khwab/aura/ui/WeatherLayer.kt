package com.toblad.khwab.aura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.model.WeatherEffectStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// WEATHER LAYER  —  Phase 10 realism pass
//
// Renders: rain (depth-separated, time-tinted), snow (mixed forms, atmospheric),
//          fog (multi-layer volumetric haze), lightning (atmospheric illumination).
//
// Architecture rules respected:
//   - LightningBus preserved exactly.
//   - LocalWindIntensity used for wind angle / drift.
//   - rememberIsResumed + animationsEnabled gates respected.
//   - All particle data built once with `remember`; updated in-place.
//   - Single shared animation coroutine per effect — no per-particle coroutines.
//   - No per-frame heap allocations (no buildList / buildMap in draw loops).
//   - No external assets, bitmaps, or shaders.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Time-of-day tint helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a subtle atmospheric tint colour that rain streaks inherit from the
 * current sky.  The tint is extremely restrained — rain must never look coloured,
 * only atmospheric.
 *
 * The base palette is grey-white.  Warm phases shift it very slightly warm;
 * night phases shift it very slightly cool.
 */
private fun rainAtmosphericTint(phase: TimePhase): Color = when (phase) {
    TimePhase.MIDNIGHT,
    TimePhase.NIGHT      -> Color(0xFFBEC8CF)   // slightly cooler grey-white at night
    TimePhase.PRE_DAWN   -> Color(0xFFC4CDD4)   // cold pre-dawn grey
    TimePhase.SUNRISE    -> Color(0xFFD2C5BC)   // very faint warm horizon hint
    TimePhase.MORNING    -> Color(0xFFCDD6DC)   // cool neutral grey-white
    TimePhase.NOON       -> Color(0xFFD5DADD)   // palest grey, high contrast sky
    TimePhase.AFTERNOON  -> Color(0xFFD0D8DC)   // similar to noon
    TimePhase.SUNSET     -> Color(0xFFD0C4B8)   // very subtle warm grey
    TimePhase.EVENING    -> Color(0xFFC5CBD2)   // cooling dusk
}

/**
 * Four slightly varied sub-tones around the base tint, eliminating the
 * "every drop is the same colour" look while never becoming saturated.
 *
 * The variants are generated procedurally around [base] with ±8 per-channel
 * variation so drop colours are almost indistinguishable individually.
 */
private fun buildRainPalette(base: Color, rng: Random): Array<Color> {
    val r = base.red
    val g = base.green
    val b = base.blue
    val d = 0.06f   // max per-channel deviation
    return Array(6) {
        Color(
            red   = (r + (rng.nextFloat() - 0.5f) * d).coerceIn(0f, 1f),
            green = (g + (rng.nextFloat() - 0.5f) * d).coerceIn(0f, 1f),
            blue  = (b + (rng.nextFloat() - 0.5f) * d).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}

/**
 * Returns the ambient fog tint for the current time phase.
 *
 * Fog is never pure white — it reflects the atmospheric light of the sky.
 */
private fun fogColor(phase: TimePhase): Color = when (phase) {
    TimePhase.PRE_DAWN   -> Color(0xFF8FA8B8)
    TimePhase.SUNRISE    -> Color(0xFFBDA898)
    TimePhase.MORNING    -> Color(0xFFADBCC8)
    TimePhase.NOON       -> Color(0xFFB8C4CC)
    TimePhase.AFTERNOON  -> Color(0xFFB0BFC8)
    TimePhase.SUNSET     -> Color(0xFFBBA090)
    TimePhase.EVENING    -> Color(0xFF8895A5)
    TimePhase.NIGHT      -> Color(0xFF6E7E8E)
    TimePhase.MIDNIGHT   -> Color(0xFF62727F)
}

/** Snow colours — cool neutral whites, never glowing. */
private val SNOW_BASE_COLORS = arrayOf(
    Color(0xFFF2F4F5),
    Color(0xFFE8EDF0),
    Color(0xFFFFFFFF),
    Color(0xFFECF0F2),
    Color(0xFFDDE4E9),   // slightly bluer cool-white
)

private fun randomSnowColor(rng: Random) = SNOW_BASE_COLORS[rng.nextInt(SNOW_BASE_COLORS.size)]

// ─────────────────────────────────────────────────────────────────────────────
// Depth enum — shared by rain and snow
// ─────────────────────────────────────────────────────────────────────────────

private enum class Depth { BACK, MID, FRONT }

// ─────────────────────────────────────────────────────────────────────────────
// RAIN PARTICLE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One rain streak.
 *
 * Immutable physical properties + mutable position.
 *
 * [depth] determines visual weight — BACK is tiny and faint, FRONT is
 * slightly stronger.  Both remain translucent and grey-white.
 *
 * [angleJitter] is a per-drop deviation around the base wind angle so streaks
 * are never perfectly parallel even in calm conditions.  BACK drops carry a
 * slightly wider jitter range, simulating how distant precipitation appears
 * less coherent.
 */
private class RainDrop(
    var  x:           Float,    // normalised 0..1
    var  y:           Float,    // normalised 0..1
    val  speed:       Float,    // fall speed (normalised units per tick)
    val  color:       Color,    // atmospheric grey-white, subtly varied
    val  depth:       Depth,
    val  angleJitter: Float,    // deviation from base wind angle (radians)
    val  baseAlpha:   Float,    // base opacity — varies by depth/speed
    val  strokeWidth: Float     // pixels
)

// ─────────────────────────────────────────────────────────────────────────────
// SNOW PARTICLE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One snow particle.
 *
 * Visual types:
 *   0 — tiny atmospheric point (dominant background and midground)
 *   1 — 3-arm wispy crystal (mid and front, less common than before)
 *   2 — soft diffuse circle (all layers)
 *   3 — larger foreground speck (front only, rare; NOT a decorative snowflake)
 *
 * Type-1 is deliberately asymmetric (3 arms, not 4 or 6) so it does not
 * resemble a geometric snowflake icon.  The arm lengths vary per-draw.
 *
 * [driftAmp] — amplitude of the sinusoidal side-to-side swaying that real
 * snowflakes exhibit as they fall.  Driven by [driftPhase] accumulated per tick.
 */
private class SnowFlake(
    var  x:          Float,
    var  y:          Float,
    val  speed:      Float,     // fall speed (normalised units per tick)
    val  driftBase:  Float,     // base horizontal drift direction (normalised/tick)
    val  driftAmp:   Float,     // sway amplitude (normalised units per tick)
    val  driftFreq:  Float,     // sway oscillation frequency (radians/tick)
    var  driftPhase: Float,     // mutable sway phase accumulator
    val  color:      Color,
    val  depth:      Depth,
    val  type:       Int,       // 0..3 visual form
    val  radius:     Float,     // draw radius in pixels
    val  baseAlpha:  Float,
    var  rotation:   Float,     // radians
    val  rotSpeed:   Float      // radians per tick
)

// ─────────────────────────────────────────────────────────────────────────────
// FOG LAYER
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One atmospheric fog density region.
 *
 * [xA] / [xB] are two independent horizontal phase offsets for the two
 * overlapping ovals that make up this layer's contribution.
 *
 * [phaseA] / [phaseB] drive a sinusoidal vertical ripple (extremely low
 * amplitude) that subtly oscillates the oval height, preventing the band
 * from looking like a static horizontal stripe.
 */
private class FogLayer(
    var  xA:         Float,
    var  xB:         Float,
    var  phaseA:     Float,   // vertical ripple phase accumulator
    var  phaseB:     Float,
    val  yFrac:      Float,   // vertical centre 0..1 — FIXED
    val  speedA:     Float,   // primary horizontal drift speed (signed)
    val  speedB:     Float,   // secondary horizontal drift speed (signed)
    val  rippleSpd:  Float,   // vertical ripple angular speed (rad/tick)
    val  rippleAmp:  Float,   // vertical ripple amplitude (fraction of h)
    val  widthA:     Float,   // primary oval width (canvas-width fraction)
    val  widthB:     Float,
    val  heightA:    Float,   // primary oval height (canvas-height fraction)
    val  heightB:    Float,
    val  alphaA:     Float,
    val  alphaB:     Float,
    val  vertSpan:   Float    // static vertical offset for sub-oval
)

// ─────────────────────────────────────────────────────────────────────────────
// LIGHTNING STATE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Transient description of one lightning event.
 *
 * Built fresh for each flash event from the LightningBus.
 *
 * [boltVisible]  — true for closer/medium events; false for purely distant ones.
 * [boltX]        — normalised horizontal trunk start.
 * [segments]     — (dx, dy) normalised-canvas deltas for each trunk segment.
 * [branches]     — (segIdx, deltaList): branch starts from the trunk point
 *                  reached after [segIdx] segments.
 * [pulseCount]   — 1, 2, or 3: how many light pulses this event produces.
 * [pulse2Frac]   — relative alpha of the second pulse (0..1).
 * [pulse3Frac]   — relative alpha of the third pulse (only used when pulseCount=3).
 */
private class LightningEvent(
    val flashAlpha:  Float,
    val flashColorR: Float,
    val flashColorG: Float,
    val flashColorB: Float,
    val boltVisible: Boolean,
    val boltX:       Float,
    val boltAlpha:   Float,
    val boltWidth:   Float,
    val segments:    List<Pair<Float, Float>>,
    val branches:    List<Pair<Int, List<Pair<Float, Float>>>>,
    val pulseCount:  Int,      // 1, 2, or 3
    val pulse2Frac:  Float,    // 0..1 relative brightness of second pulse
    val pulse3Frac:  Float     // 0..1 relative brightness of third pulse
)

// ─────────────────────────────────────────────────────────────────────────────
// Lightning geometry builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds a randomised [LightningEvent].
 *
 * Three distance categories:
 *   DISTANT (50 %) — atmospheric bloom only; no visible bolt.
 *   MEDIUM  (30 %) — faint bolt, moderate flash.
 *   CLOSE   (20 %) — visible branching bolt, stronger flash.
 *
 * Pulse count is randomised independently from distance category, so
 * distant storms can occasionally produce two or three weak pulses.
 */
private fun buildLightningEvent(intensity: Float, rng: Random): LightningEvent {

    val roll      = rng.nextFloat()
    val isDistant = roll < 0.50f
    val isMedium  = roll < 0.80f   // 0.50..0.80

    // Flash colour: cool white with slight blue cast
    val flashR = 0.88f + rng.nextFloat() * 0.10f
    val flashG = 0.88f + rng.nextFloat() * 0.10f
    val flashB = 0.92f + rng.nextFloat() * 0.08f

    // Flash alpha: distant is dimmer; global cap reduced to avoid screen-flash look
    val baseFactor = 0.25f + intensity * 0.35f
    val flashAlpha = when {
        isDistant -> baseFactor * (0.25f + rng.nextFloat() * 0.20f)
        isMedium  -> baseFactor * (0.50f + rng.nextFloat() * 0.25f)
        else      -> baseFactor * (0.70f + rng.nextFloat() * 0.20f)
    }.coerceIn(0f, 0.65f)

    // Pulse sequence: 1, 2, or 3 pulses
    // Distant events mostly single-pulse; close events more likely multi-pulse
    val pulseRoll = rng.nextFloat()
    val pulseCount = when {
        isDistant -> if (pulseRoll < 0.65f) 1 else if (pulseRoll < 0.90f) 2 else 3
        isMedium  -> if (pulseRoll < 0.45f) 1 else if (pulseRoll < 0.80f) 2 else 3
        else      -> if (pulseRoll < 0.25f) 1 else if (pulseRoll < 0.65f) 2 else 3
    }
    // Second and third pulses are weaker — second 15–45% strength, third 8–20%
    val pulse2Frac = 0.15f + rng.nextFloat() * 0.30f
    val pulse3Frac = 0.08f + rng.nextFloat() * 0.12f

    if (isDistant) {
        return LightningEvent(
            flashAlpha  = flashAlpha,
            flashColorR = flashR,
            flashColorG = flashG,
            flashColorB = flashB,
            boltVisible = false,
            boltX       = 0f,
            boltAlpha   = 0f,
            boltWidth   = 0f,
            segments    = emptyList(),
            branches    = emptyList(),
            pulseCount  = pulseCount,
            pulse2Frac  = pulse2Frac,
            pulse3Frac  = pulse3Frac
        )
    }

    val boltX     = 0.15f + rng.nextFloat() * 0.70f
    val boltAlpha = if (isMedium) 0.30f + rng.nextFloat() * 0.30f
                    else          0.55f + rng.nextFloat() * 0.30f
    val boltWidth = if (isMedium) 0.9f  + rng.nextFloat() * 0.9f
                    else          1.3f  + rng.nextFloat() * 1.4f

    // Trunk: 6–10 segments
    val segCount = 6 + rng.nextInt(5)
    val segments = buildList {
        repeat(segCount) {
            val dy = 0.045f + rng.nextFloat() * 0.055f
            val dx = (rng.nextFloat() - 0.5f) * 0.06f
            add(Pair(dx, dy))
        }
    }

    // Branches: 0–1 for medium, 1–2 for close
    val branchCount = if (isMedium) if (rng.nextBoolean()) 1 else 0
                      else          1 + rng.nextInt(2)
    val branches = buildList {
        val usedSegs = mutableSetOf<Int>()
        repeat(branchCount) {
            var segIdx = 1 + rng.nextInt((segCount - 2).coerceAtLeast(1))
            while (segIdx in usedSegs && usedSegs.size < segCount - 2) {
                segIdx = 1 + rng.nextInt((segCount - 2).coerceAtLeast(1))
            }
            usedSegs.add(segIdx)
            val branchSegs = 2 + rng.nextInt(3)
            val branchDelta = buildList {
                repeat(branchSegs) {
                    val dy = 0.030f + rng.nextFloat() * 0.040f
                    val dx = (rng.nextFloat() - 0.5f) * 0.08f +
                             (if (rng.nextBoolean()) 0.03f else -0.03f)
                    add(Pair(dx, dy))
                }
            }
            add(Pair(segIdx, branchDelta))
        }
    }

    return LightningEvent(
        flashAlpha  = flashAlpha,
        flashColorR = flashR,
        flashColorG = flashG,
        flashColorB = flashB,
        boltVisible = true,
        boltX       = boltX,
        boltAlpha   = boltAlpha,
        boltWidth   = boltWidth,
        segments    = segments,
        branches    = branches,
        pulseCount  = pulseCount,
        pulse2Frac  = pulse2Frac,
        pulse3Frac  = pulse3Frac
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Builder helpers — rain & snow particle lists
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds the rain particle list.
 *
 * Population: 55 % BACK · 35 % MID · 10 % FRONT.
 * RAIN: ~110 drops; STORM: up to ~165 drops.
 *
 * [rainPalette] is a 6-element array of subtly varied grey-white colours built
 * from the time-of-day tint — passed in so particles are baked with the right
 * atmospheric tint at construction time.
 *
 * Per-depth angle jitter ranges:
 *   BACK  — ±0.09 rad (wider: distant rain looks less coherent)
 *   MID   — ±0.07 rad
 *   FRONT — ±0.05 rad (tighter: closer rain is more coherent)
 */
private fun buildRainDrops(
    intense:     Boolean,
    severity:    Float,
    rainPalette: Array<Color>,
    rng:         Random
): List<RainDrop> {
    val baseCount = if (intense) 155 else 110
    val total = (baseCount * (0.70f + severity * 0.60f)).toInt().coerceAtMost(200)
    return buildList {
        repeat(total) {
            val roll  = rng.nextFloat()
            val depth = when {
                roll < 0.55f -> Depth.BACK
                roll < 0.90f -> Depth.MID
                else         -> Depth.FRONT
            }
            // floatArrayOf(speedMin, speedMax, alphaMin, alphaMax, strokeMin, strokeMax)
            val p = when (depth) {
                Depth.BACK  -> if (intense) floatArrayOf(0.0055f, 0.0090f, 0.10f, 0.22f, 0.65f, 1.05f)
                               else         floatArrayOf(0.0040f, 0.0075f, 0.08f, 0.20f, 0.55f, 0.95f)
                Depth.MID   -> if (intense) floatArrayOf(0.0090f, 0.0140f, 0.20f, 0.36f, 0.95f, 1.55f)
                               else         floatArrayOf(0.0070f, 0.0110f, 0.16f, 0.30f, 0.85f, 1.35f)
                Depth.FRONT -> if (intense) floatArrayOf(0.0150f, 0.0200f, 0.28f, 0.46f, 1.30f, 1.90f)
                               else         floatArrayOf(0.0110f, 0.0160f, 0.22f, 0.38f, 1.10f, 1.70f)
            }
            val speed       = p[0] + rng.nextFloat() * (p[1] - p[0])
            val baseAlpha   = p[2] + rng.nextFloat() * (p[3] - p[2])
            val strokeWidth = p[4] + rng.nextFloat() * (p[5] - p[4])

            // Angle jitter: BACK drops have wider spread
            val jitterRange = when (depth) {
                Depth.BACK  -> 0.18f
                Depth.MID   -> 0.14f
                Depth.FRONT -> 0.10f
            }
            add(RainDrop(
                x           = rng.nextFloat(),
                y           = rng.nextFloat(),
                speed       = speed,
                color       = rainPalette[rng.nextInt(rainPalette.size)],
                depth       = depth,
                angleJitter = (rng.nextFloat() - 0.5f) * jitterRange,
                baseAlpha   = baseAlpha,
                strokeWidth = strokeWidth
            ))
        }
    }
}

/**
 * Rain streak length in canvas pixels, correlated with depth and speed.
 *
 * BACK streaks: short — they represent distant precipitation.
 * FRONT streaks: longer but still restrained (never giant slashes).
 */
private fun rainStreakLength(drop: RainDrop, canvasH: Float): Float {
    val baseLen = when (drop.depth) {
        Depth.BACK  -> canvasH * (0.016f + drop.speed * 1.1f)
        Depth.MID   -> canvasH * (0.026f + drop.speed * 1.4f)
        Depth.FRONT -> canvasH * (0.038f + drop.speed * 1.7f)
    }
    return baseLen.coerceIn(3f, canvasH * 0.075f)
}

/**
 * Builds the snow particle list.
 *
 * Population: 60 % BACK type-0 · 28 % MID types 0–2 · 12 % FRONT types 1–3.
 * Total: 90 flakes.
 *
 * Sway (driftAmp / driftFreq / driftPhase) gives each flake a gentle oscillating
 * lateral motion that real snowflakes exhibit as they fall.
 */
private fun buildSnowFlakes(rng: Random): List<SnowFlake> = buildList {
    repeat(90) {
        val roll  = rng.nextFloat()
        val depth = when {
            roll < 0.60f -> Depth.BACK
            roll < 0.88f -> Depth.MID
            else         -> Depth.FRONT
        }
        // Type selection — note: type-1 (3-arm crystal) is less frequent to
        // avoid the sky looking like it is filled with snowflake icons.
        val type = when (depth) {
            Depth.BACK  -> 0                           // always tiny point
            Depth.MID   -> if (rng.nextFloat() < 0.60f) 0   // 60 % point
                           else if (rng.nextFloat() < 0.55f) 2  // 22 % soft circle
                           else 1                              // 18 % wispy crystal
            Depth.FRONT -> if (rng.nextFloat() < 0.30f) 3   // 30 % large speck
                           else if (rng.nextFloat() < 0.55f) 2  // ~38 % soft circle
                           else if (rng.nextFloat() < 0.55f) 1  // ~17 % wispy crystal
                           else 0                              // ~15 % point
        }
        // floatArrayOf(speedMin, speedMax, radiusMin, radiusMax, alphaMin, alphaMax)
        val q = when (depth) {
            Depth.BACK  -> floatArrayOf(0.0008f, 0.0018f, 0.9f,  1.8f,  0.10f, 0.26f)
            Depth.MID   -> floatArrayOf(0.0018f, 0.0038f, 1.6f,  3.0f,  0.26f, 0.50f)
            Depth.FRONT -> floatArrayOf(0.0035f, 0.0058f, 2.5f,  4.2f,  0.40f, 0.65f)
        }
        val speed     = q[0] + rng.nextFloat() * (q[1] - q[0])
        val radius    = q[2] + rng.nextFloat() * (q[3] - q[2])
        val baseAlpha = q[4] + rng.nextFloat() * (q[5] - q[4])

        val driftBase = (rng.nextFloat() - 0.5f) * when (depth) {
            Depth.BACK  -> 0.0005f
            Depth.MID   -> 0.0010f
            Depth.FRONT -> 0.0018f
        }
        // Sinusoidal sway — realistic pendulum-like lateral motion
        val driftAmp  = when (depth) {
            Depth.BACK  -> 0.0002f + rng.nextFloat() * 0.0003f
            Depth.MID   -> 0.0004f + rng.nextFloat() * 0.0006f
            Depth.FRONT -> 0.0007f + rng.nextFloat() * 0.0010f
        }
        val driftFreq = 0.020f + rng.nextFloat() * 0.040f   // individual oscillation speed

        // Type 0 particles barely rotate; others have gentle independent rotation
        val rotSpeed = when (type) {
            0    -> 0f
            1    -> (rng.nextFloat() - 0.5f) * 0.018f   // gentle 3-arm rotation
            else -> (rng.nextFloat() - 0.5f) * 0.010f
        }

        add(SnowFlake(
            x          = rng.nextFloat(),
            y          = rng.nextFloat(),
            speed      = speed,
            driftBase  = driftBase,
            driftAmp   = driftAmp,
            driftFreq  = driftFreq,
            driftPhase = rng.nextFloat() * (2f * PI.toFloat()),
            color      = randomSnowColor(rng),
            depth      = depth,
            type       = type,
            radius     = radius,
            baseAlpha  = baseAlpha,
            rotation   = rng.nextFloat() * (2f * PI.toFloat()),
            rotSpeed   = rotSpeed
        ))
    }
}

/**
 * Builds the fog layer list.
 *
 * 10 layers at different vertical positions with increasing density toward the
 * horizon.  Each layer has two overlapping ovals drifting at slightly different
 * speeds.  A sinusoidal vertical ripple (very low amplitude) prevents layers
 * from appearing as static horizontal bands.
 *
 * Wide oval widths (0.85–1.70 canvas-widths) ensure heavy overlap so no
 * identifiable oval silhouettes are visible.
 */
private fun buildFogLayers(rng: Random): List<FogLayer> = buildList {
    // (yFrac, alphaA, alphaB) — density increases toward horizon
    val configs = listOf(
        Triple(0.08f, 0.030f, 0.022f),
        Triple(0.18f, 0.045f, 0.035f),
        Triple(0.28f, 0.060f, 0.048f),
        Triple(0.38f, 0.075f, 0.060f),
        Triple(0.48f, 0.090f, 0.072f),
        Triple(0.58f, 0.105f, 0.085f),
        Triple(0.67f, 0.120f, 0.096f),
        Triple(0.76f, 0.140f, 0.112f),
        Triple(0.86f, 0.160f, 0.130f),
        Triple(0.94f, 0.185f, 0.150f),
    )
    for ((yFrac, alphaA, alphaB) in configs) {
        // Vary aspect ratios to eliminate horizontal-band appearance.
        // Ovals near the horizon are wider and squatter; higher ones are
        // slightly narrower and taller — matching real fog behaviour.
        val horizonBias = yFrac                      // 0 at top, ~1 at horizon
        val widthA  = 0.85f + horizonBias * 0.85f + rng.nextFloat() * 0.25f
        val widthB  = 0.75f + horizonBias * 0.75f + rng.nextFloat() * 0.20f
        val heightA = 0.04f + (1f - horizonBias) * 0.06f + rng.nextFloat() * 0.05f
        val heightB = 0.03f + (1f - horizonBias) * 0.05f + rng.nextFloat() * 0.04f

        val speedA   = (0.000035f + rng.nextFloat() * 0.000065f) * if (rng.nextBoolean()) 1f else -1f
        val speedB   = (0.000022f + rng.nextFloat() * 0.000050f) * if (rng.nextBoolean()) 1f else -1f

        // Vertical ripple — extremely subtle; prevents static-band look
        val rippleSpd = 0.0008f + rng.nextFloat() * 0.0015f
        val rippleAmp = 0.003f  + rng.nextFloat() * 0.005f

        add(FogLayer(
            xA        = rng.nextFloat(),
            xB        = rng.nextFloat(),
            phaseA    = rng.nextFloat() * (2f * PI.toFloat()),
            phaseB    = rng.nextFloat() * (2f * PI.toFloat()),
            yFrac     = yFrac,
            speedA    = speedA,
            speedB    = speedB,
            rippleSpd = rippleSpd,
            rippleAmp = rippleAmp,
            widthA    = widthA,
            widthB    = widthB,
            heightA   = heightA,
            heightB   = heightB,
            alphaA    = alphaA,
            alphaB    = alphaB,
            vertSpan  = rng.nextFloat() * 0.022f
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas draw helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws one snow particle.
 *
 * Type 0 — tiny atmospheric point
 * Type 1 — 3-arm asymmetric wispy crystal (NOT a decorative snowflake)
 *           Three arms at 0°, 110°, 230° (irregular spacing) with slightly
 *           different lengths so it looks like a tumbling ice crystal fragment.
 * Type 2 — soft diffuse circle (two-layer: faint outer + brighter core)
 * Type 3 — larger foreground speck (same two-layer but slightly bigger)
 */
private fun DrawScope.drawSnowFlake(flake: SnowFlake, alpha: Float) {
    val cx = flake.x * size.width
    val cy = flake.y * size.height
    val r  = flake.radius
    val c  = flake.color.copy(alpha = alpha)

    when (flake.type) {
        0 -> {
            drawCircle(color = c, radius = r, center = Offset(cx, cy))
        }
        1 -> {
            // 3-arm wispy crystal — irregular arm angles and lengths
            val angles = floatArrayOf(
                flake.rotation,
                flake.rotation + (110f * PI.toFloat() / 180f),
                flake.rotation + (230f * PI.toFloat() / 180f)
            )
            // Arms have slightly different lengths — simulates real ice crystal asymmetry
            val armLens = floatArrayOf(r * 1.50f, r * 1.30f, r * 1.65f)
            for (i in 0..2) {
                val armCos = cos(angles[i])
                val armSin = sin(angles[i])
                val len    = armLens[i]
                drawLine(
                    color       = c,
                    start       = Offset(cx - armCos * len * 0.35f, cy - armSin * len * 0.35f),
                    end         = Offset(cx + armCos * len, cy + armSin * len),
                    strokeWidth = 0.85f,
                    cap         = StrokeCap.Round
                )
            }
        }
        2 -> {
            // Soft diffuse circle
            drawCircle(color = c.copy(alpha = alpha * 0.70f), radius = r * 1.5f, center = Offset(cx, cy))
            drawCircle(color = c,                              radius = r * 0.65f, center = Offset(cx, cy))
        }
        else -> {
            // Slightly larger foreground speck
            drawCircle(color = c.copy(alpha = alpha * 0.28f), radius = r * 2.0f, center = Offset(cx, cy))
            drawCircle(color = c,                              radius = r,         center = Offset(cx, cy))
        }
    }
}

/**
 * Draws the lightning bolt trunk and branches from [event].
 *
 * Each trunk segment is pre-baked with lateral deviation in [event.segments].
 * The bolt is drawn at the given [alpha] multiplier (which animates to 0).
 */
private fun DrawScope.drawLightningBolt(event: LightningEvent, alpha: Float) {
    if (!event.boltVisible || event.segments.isEmpty()) return

    val w = size.width
    val h = size.height

    val trunkPoints = mutableListOf<Offset>()
    var cx = event.boltX * w
    var cy = h * 0.05f
    trunkPoints.add(Offset(cx, cy))
    for ((dx, dy) in event.segments) {
        cx += dx * w
        cy += dy * h
        trunkPoints.add(Offset(cx, cy))
    }

    val strokeAlpha = (event.boltAlpha * alpha).coerceIn(0f, 1f)
    val boltColor = Color(
        red   = event.flashColorR * 0.92f,
        green = event.flashColorG * 0.90f,
        blue  = event.flashColorB,
        alpha = strokeAlpha
    )

    for (i in 0 until trunkPoints.size - 1) {
        drawLine(
            color       = boltColor,
            start       = trunkPoints[i],
            end         = trunkPoints[i + 1],
            strokeWidth = event.boltWidth,
            cap         = StrokeCap.Round
        )
    }

    val branchColor = boltColor.copy(alpha = (strokeAlpha * 0.60f).coerceIn(0f, 1f))
    val branchWidth = (event.boltWidth * 0.50f).coerceAtLeast(0.7f)
    for ((segIdx, deltas) in event.branches) {
        if (segIdx >= trunkPoints.size) continue
        var bx = trunkPoints[segIdx].x
        var by = trunkPoints[segIdx].y
        for ((dx, dy) in deltas) {
            val nx = bx + dx * w
            val ny = by + dy * h
            drawLine(
                color       = branchColor,
                start       = Offset(bx, by),
                end         = Offset(nx, ny),
                strokeWidth = branchWidth,
                cap         = StrokeCap.Round
            )
            bx = nx
            by = ny
        }
    }
}

/**
 * Draws the atmospheric illumination for a single lightning pulse.
 *
 * The effect has three concentric components:
 *   Outer ring — broad sky brightening at very low alpha (≤ 12%).
 *   Mid ring   — cloud-region illumination at moderate alpha (≤ 30%).
 *   Inner ring — bright core near bolt origin (≤ 55%).
 *
 * A residual full-screen tint is kept at ≤ 20% of the event flashAlpha to
 * suggest the entire sky briefly reacts, without a white-screen look.
 *
 * [pulseMult] scales the whole effect (1.0 for first pulse, smaller for
 * subsequent pulses).
 */
private fun DrawScope.drawLightningFlash(
    event:     LightningEvent,
    alpha:     Float,
    pulseMult: Float
) {
    val w = size.width
    val h = size.height

    val a = alpha * pulseMult

    // Full-screen residual tint — very restrained (≤ 20% cap)
    val globalAlpha = (a * 0.20f).coerceIn(0f, 0.20f)
    drawRect(
        color    = Color(
            red   = event.flashColorR,
            green = event.flashColorG,
            blue  = event.flashColorB,
            alpha = globalAlpha
        ),
        topLeft  = Offset.Zero,
        size     = Size(w, h)
    )

    // Bloom centre: bolt position for visible events; pseudorandom for distant
    val effectiveBoltX = if (event.boltVisible) event.boltX
                         else (event.flashColorR * 0.7f + 0.15f)
    val bX = effectiveBoltX * w
    val bY = h * 0.18f

    val bloomAlpha = (a * 0.90f).coerceIn(0f, 1f)

    // Ring 3 — broad atmospheric sky brightening
    drawCircle(
        color  = Color(
            red   = event.flashColorR,
            green = event.flashColorG,
            blue  = event.flashColorB,
            alpha = bloomAlpha * 0.10f
        ),
        radius = w * 0.80f,
        center = Offset(bX, bY)
    )
    // Ring 2 — cloud-region illumination
    drawCircle(
        color  = Color(
            red   = event.flashColorR,
            green = event.flashColorG,
            blue  = event.flashColorB,
            alpha = bloomAlpha * 0.28f
        ),
        radius = w * 0.42f,
        center = Offset(bX, bY)
    )
    // Ring 1 — bright core
    drawCircle(
        color  = Color(
            red   = event.flashColorR,
            green = event.flashColorG,
            blue  = event.flashColorB,
            alpha = bloomAlpha * 0.52f
        ),
        radius = w * 0.18f,
        center = Offset(bX, bY)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP-LEVEL COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WeatherLayer(theme: AuraTheme) {

    val effect  = theme.profile.weatherEffect
    val isStorm = effect == WeatherEffectStyle.STORM

    // Keep LightningBus in sync with storm state — unchanged from original
    LaunchedEffect(isStorm, theme.profile.stormIntensity) {
        LightningBus.update(
            stormActive = isStorm,
            intensity   = theme.profile.stormIntensity
        )
    }

    if (effect == WeatherEffectStyle.NONE) return

    val isResumed         by rememberIsResumed()
    val animationsEnabled = theme.animationsEnabled
    val wind              = LocalWindIntensity.current

    when (effect) {
        WeatherEffectStyle.RAIN,
        WeatherEffectStyle.STORM -> {
            AnimatedRain(
                intense           = isStorm,
                intensity         = theme.profile.stormIntensity,
                wind              = wind,
                timePhase         = theme.timePhase,
                isResumed         = isResumed,
                animationsEnabled = animationsEnabled
            )
        }
        WeatherEffectStyle.SNOW -> {
            AnimatedSnow(
                wind              = wind,
                timePhase         = theme.timePhase,
                isResumed         = isResumed,
                animationsEnabled = animationsEnabled
            )
        }
        WeatherEffectStyle.FOG -> {
            AnimatedFog(
                timePhase         = theme.timePhase,
                wind              = wind,
                isResumed         = isResumed,
                animationsEnabled = animationsEnabled
            )
        }
        else -> Unit
    }

    if (isStorm) {
        LightningEffect(
            intensity         = theme.profile.stormIntensity,
            isResumed         = isResumed,
            animationsEnabled = animationsEnabled
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RAIN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedRain(
    intense:           Boolean,
    intensity:         Float,
    wind:              Float,
    timePhase:         TimePhase,
    isResumed:         Boolean,
    animationsEnabled: Boolean
) {
    val severity = intensity.coerceIn(0f, 1f)

    // Wind angle in mutableState so the physics loop reads the current value.
    // ~4° at calm wind → ~38° at full storm wind.
    val windAngleState = remember { mutableFloatStateOf(0.07f + wind * 0.59f) }
    windAngleState.floatValue = 0.07f + wind * 0.59f

    // Build rain palette from the time-of-day tint.
    // Remember per timePhase: palette only needs rebuilding when the phase changes
    // (which is infrequent).
    val rainPalette = remember(timePhase) {
        buildRainPalette(rainAtmosphericTint(timePhase), Random)
    }

    val drops = remember(intense, severity) {
        mutableStateListOf<RainDrop>().also { list ->
            list.addAll(buildRainDrops(intense, severity, rainPalette, Random))
        }
    }

    LaunchedEffect(intense, severity, isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val currentAngle = windAngleState.floatValue
            for (drop in drops) {
                val angle = currentAngle + drop.angleJitter
                drop.y += drop.speed
                drop.x += sin(angle) * drop.speed * 0.30f
                if (drop.y > 1.05f) {
                    drop.y = -0.05f
                    drop.x = Random.nextFloat()
                }
                if (drop.x > 1.10f) drop.x = -0.05f
                if (drop.x < -0.10f) drop.x = 1.05f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val currentAngle = windAngleState.floatValue
        for (drop in drops) {
            val cx    = drop.x * w
            val cy    = drop.y * h
            val angle = currentAngle + drop.angleJitter
            val len   = rainStreakLength(drop, h)

            // Atmospheric horizon fade:
            // Background drops represent distant precipitation — they fade into
            // atmospheric haze earlier (starting at y=0.60).  Midground fades
            // from y=0.72.  Foreground stays visible almost to the bottom.
            val horizonFactor = when (drop.depth) {
                Depth.BACK  -> when {
                    drop.y < 0.60f -> 1.0f
                    drop.y < 0.88f -> 1f - (drop.y - 0.60f) / 0.28f * 0.65f
                    else           -> 0.35f
                }
                Depth.MID   -> when {
                    drop.y < 0.72f -> 1.0f
                    drop.y < 0.93f -> 1f - (drop.y - 0.72f) / 0.21f * 0.48f
                    else           -> 0.52f
                }
                Depth.FRONT -> 1.0f
            }

            val alpha = (drop.baseAlpha * horizonFactor).coerceIn(0f, 1f)
            if (alpha < 0.005f) continue

            drawLine(
                color       = drop.color.copy(alpha = alpha),
                start       = Offset(cx, cy),
                end         = Offset(cx + sin(angle) * len, cy + cos(angle) * len),
                strokeWidth = drop.strokeWidth,
                cap         = StrokeCap.Round
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SNOW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedSnow(
    wind:              Float,
    timePhase:         TimePhase,
    isResumed:         Boolean,
    animationsEnabled: Boolean
) {
    val flakes = remember {
        mutableStateListOf<SnowFlake>().also { list ->
            list.addAll(buildSnowFlakes(Random))
        }
    }

    val windState = remember { mutableFloatStateOf(wind) }
    windState.floatValue = wind

    // Time-of-day visibility.  Snow contrast is lower at night (blends into dark sky);
    // by day it is more visible against a lighter sky.
    val timeVis = when (timePhase) {
        TimePhase.NOON, TimePhase.AFTERNOON  -> 1.00f
        TimePhase.MORNING                    -> 0.90f
        TimePhase.SUNRISE, TimePhase.SUNSET  -> 0.72f
        TimePhase.EVENING, TimePhase.PRE_DAWN -> 0.52f
        TimePhase.NIGHT, TimePhase.MIDNIGHT  -> 0.38f
    }

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val currentWind = windState.floatValue
            for (flake in flakes) {
                val windDrift = currentWind * when (flake.depth) {
                    Depth.BACK  -> 0.0005f
                    Depth.MID   -> 0.0010f
                    Depth.FRONT -> 0.0018f
                }
                // Sinusoidal lateral sway
                val sway = sin(flake.driftPhase) * flake.driftAmp
                flake.driftPhase += flake.driftFreq
                flake.y += flake.speed
                flake.x += flake.driftBase + windDrift + sway
                flake.rotation += flake.rotSpeed
                if (flake.y > 1.05f) {
                    flake.y = -0.03f
                    flake.x = Random.nextFloat()
                }
                if (flake.x > 1.06f) flake.x = -0.04f
                if (flake.x < -0.06f) flake.x = 1.04f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        for (flake in flakes) {
            // Atmospheric horizon fade for snow — same principle as rain.
            // Distant background flakes fade near the horizon.
            val horizonFactor = when (flake.depth) {
                Depth.BACK  -> when {
                    flake.y < 0.65f -> 1.0f
                    flake.y < 0.90f -> 1f - (flake.y - 0.65f) / 0.25f * 0.60f
                    else            -> 0.40f
                }
                Depth.MID   -> when {
                    flake.y < 0.78f -> 1.0f
                    flake.y < 0.96f -> 1f - (flake.y - 0.78f) / 0.18f * 0.35f
                    else            -> 0.65f
                }
                Depth.FRONT -> 1.0f
            }
            val alpha = (flake.baseAlpha * timeVis * horizonFactor).coerceIn(0f, 1f)
            if (alpha > 0.005f) drawSnowFlake(flake, alpha)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedFog(
    timePhase:         TimePhase,
    wind:              Float,
    isResumed:         Boolean,
    animationsEnabled: Boolean
) {
    val layers = remember {
        mutableStateListOf<FogLayer>().also { list ->
            list.addAll(buildFogLayers(Random))
        }
    }

    val baseColor = fogColor(timePhase)

    LaunchedEffect(isResumed, animationsEnabled) {
        if (!isResumed || !animationsEnabled) return@LaunchedEffect
        while (isActive) {
            for (layer in layers) {
                // Wind nudges fog slightly — fog barely reacts to wind
                val windFactor = 1f + wind * 0.35f
                layer.xA += layer.speedA * windFactor
                layer.xB += layer.speedB * windFactor
                // Advance vertical ripple phases
                layer.phaseA += layer.rippleSpd
                layer.phaseB += layer.rippleSpd * 0.73f  // slightly different frequency
                // Wrap horizontal offsets
                val wrapA = layer.widthA + 0.05f
                val wrapB = layer.widthB + 0.05f
                if (layer.xA > 1f + wrapA)  layer.xA = -wrapA
                if (layer.xA < -wrapA)       layer.xA = 1f + wrapA
                if (layer.xB > 1f + wrapB)  layer.xB = -wrapB
                if (layer.xB < -wrapB)       layer.xB = 1f + wrapB
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        for (layer in layers) {
            // Vertical ripple: extremely low amplitude height oscillation
            val rippleA = sin(layer.phaseA) * layer.rippleAmp * h
            val rippleB = sin(layer.phaseB) * layer.rippleAmp * h

            val cyA = layer.yFrac * h + rippleA
            val cyB = (layer.yFrac + layer.vertSpan) * h + rippleB

            // Primary oval
            val wA = layer.widthA * w
            val hA = layer.heightA * h
            drawOval(
                color   = baseColor.copy(alpha = layer.alphaA),
                topLeft = Offset(layer.xA * w - wA * 0.5f, cyA - hA * 0.5f),
                size    = Size(wA, hA)
            )

            // Secondary sub-oval — different position, size, and drift speed
            val wB = layer.widthB * w
            val hB = layer.heightB * h
            drawOval(
                color   = baseColor.copy(alpha = layer.alphaB),
                topLeft = Offset(layer.xB * w - wB * 0.5f, cyB - hB * 0.5f),
                size    = Size(wB, hB)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIGHTNING
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Atmospheric lightning effect driven by [LightningBus.flashes].
 *
 * Three-stage multi-pulse rendering:
 *   Stage 1 — atmospheric bloom (3 concentric circles) + residual global tint
 *   Stage 2 — optional bolt silhouette for medium/close events
 *   Stage 3 — optional secondary and tertiary weak pulses for multi-pulse events
 *
 * The [LightningBus] is not modified — only the visual rendering changes.
 *
 * A single [Animatable] tracks the current pulse alpha.  No per-bolt coroutines.
 *
 * [pulseMultState] carries the current pulse scale factor (1.0 for first pulse,
 * event.pulse2Frac for second, event.pulse3Frac for third) so [drawLightningFlash]
 * can scale the bloom intensity correctly.
 */
@Composable
private fun LightningEffect(
    intensity:         Float,
    isResumed:         Boolean,
    animationsEnabled: Boolean
) {
    val alpha            = remember { Animatable(0f) }
    var currentEvent     by remember { mutableStateOf<LightningEvent?>(null) }
    var pulseMultState   by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        LightningBus.flashes.collect {
            val event = buildLightningEvent(intensity, Random)
            currentEvent  = event
            pulseMultState = 1f

            // ── Pulse 1: bright onset → rapid fade ────────────────────────
            alpha.snapTo(event.flashAlpha)
            alpha.animateTo(0f, animationSpec = tween(durationMillis = 160))

            // ── Pulse 2 (if any) ──────────────────────────────────────────
            if (event.pulseCount >= 2) {
                delay(25L + (Random.nextFloat() * 35f).toLong())
                pulseMultState = event.pulse2Frac
                alpha.snapTo(event.flashAlpha * event.pulse2Frac)
                alpha.animateTo(0f, animationSpec = tween(durationMillis = 110))
            }

            // ── Pulse 3 (if any) ──────────────────────────────────────────
            if (event.pulseCount >= 3) {
                delay(18L + (Random.nextFloat() * 28f).toLong())
                pulseMultState = event.pulse3Frac
                alpha.snapTo(event.flashAlpha * event.pulse3Frac)
                alpha.animateTo(0f, animationSpec = tween(durationMillis = 80))
            }

            currentEvent = null
        }
    }

    val a = alpha.value
    if (a <= 0.003f) return

    val event = currentEvent ?: return

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Atmospheric bloom + residual global tint
        drawLightningFlash(event, a, pulseMultState)
        // Bolt silhouette on top (only for medium/close events)
        drawLightningBolt(event, a)
    }
}
