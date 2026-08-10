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
import androidx.compose.ui.graphics.drawscope.Stroke
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.Season
import com.toblad.khwab.aura.model.WeatherState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// BIRD LAYER — Photorealistic distant bird silhouettes
//
// Architecture rules:
//   - V-formation flock system preserved.
//   - rememberIsResumed() + animationsEnabled gated.
//   - Particle data built once via remember; updated in-place.
//   - Single shared coroutine — no per-bird coroutines.
//   - LocalWindIntensity consumed for wind angle.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Depth categories
// ─────────────────────────────────────────────────────────────────────────────

private enum class BirdDepth { FAR, MID, NEAR }

// ─────────────────────────────────────────────────────────────────────────────
// Atmospheric bird colour — continuous solar elevation model
//
// Birds at distance look like the sky's silhouette — never pure black.
// Colour responds to solar elevation and depth continuously.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the base atmospheric silhouette colour derived from solar elevation.
 *
 * The colour is always a dark desaturated tone — never pure black.
 * Depth further attenuates alpha toward atmosphere.
 *
 * [solarElev] normalised solar elevation in [-1, +1].
 *   Deep night (≤ -0.6) → near-black blue
 *   Twilight / horizon crossing (≈ 0) → subtle warm dark
 *   Full day (≥ +0.3) → cool dark blue-grey
 */
private fun birdColor(solarElev: Float, depth: BirdDepth, weather: WeatherState): Color {

    // Warm influence: strongest at horizon crossing
    val warmZone = when {
        solarElev < -0.20f -> 0f
        solarElev < 0f     -> (solarElev + 0.20f) / 0.20f
        solarElev < 0.20f  -> 1f - solarElev / 0.20f
        else               -> 0f
    }.coerceIn(0f, 1f)

    // Anchor colours — all dark/desaturated
    val nightBase    = Color(0xFF101822)   // near-black deep blue
    val twilightBase = Color(0xFF2E2218)   // warm dark — sunrise/sunset warmth
    val dayBase      = Color(0xFF1E2830)   // deep desaturated blue-grey

    val base = when {
        solarElev <= -0.10f -> nightBase
        solarElev <= 0f     -> lerpColorAtm(nightBase, twilightBase, warmZone)
        solarElev <= 0.20f  -> lerpColorAtm(twilightBase, dayBase, solarElev / 0.20f)
        else                -> dayBase
    }

    // Depth fades the silhouette into atmosphere
    val alphaBase = when (depth) {
        BirdDepth.FAR  -> 0.14f   // barely distinguishable from sky
        BirdDepth.MID  -> 0.26f   // visible but soft
        BirdDepth.NEAR -> 0.40f   // clearest — still atmospheric
    }

    // Weather suppresses visibility.
    // FOG uses a stronger per-depth suppression: far birds nearly disappear
    // into foggy air, matching how real fog obscures distance.
    val weatherFactor = when (weather) {
        WeatherState.CLEAR  -> 1.00f
        WeatherState.CLOUDY -> 0.80f
        WeatherState.FOG    -> when (depth) {
            BirdDepth.FAR  -> 0.25f   // far birds nearly invisible in fog
            BirdDepth.MID  -> 0.45f
            BirdDepth.NEAR -> 0.60f
        }
        else                -> 0.60f   // rain/snow/storm — birds barely visible
    }

    // Environmental warmth tint:
    // Blend silhouette very slightly toward ambient sky scatter so birds feel
    // embedded in the same atmospheric light.  Tint is ~5-12% — perceived as
    // "natural" not "coloured".
    val envTint = when {
        warmZone > 0.3f    -> Color(0.55f, 0.38f, 0.25f)   // warm scatter at twilight
        solarElev < -0.10f -> Color(0.20f, 0.26f, 0.40f)   // deep cool night
        else               -> Color(0.30f, 0.42f, 0.55f)   // neutral daytime sky
    }
    val tintAmt = when (depth) {
        BirdDepth.FAR  -> 0.12f
        BirdDepth.MID  -> 0.08f
        BirdDepth.NEAR -> 0.05f
    }
    val tinted = Color(
        red   = base.red   * (1f - tintAmt) + envTint.red   * tintAmt,
        green = base.green * (1f - tintAmt) + envTint.green * tintAmt,
        blue  = base.blue  * (1f - tintAmt) + envTint.blue  * tintAmt
    )

    return tinted.copy(alpha = (alphaBase * weatherFactor).coerceIn(0f, 1f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Bird data class
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One bird in the sky.
 *
 * Immutable physical properties + mutable position/phase.
 *
 * [depth] controls visual size and opacity.
 * [wingAsymmetry] is a per-bird random value that makes the two wings
 *   slightly unequal — preventing perfectly symmetric W silhouettes.
 * [isSoaring] determines whether the bird uses a slow lazy flap
 *   or a normal flapping cadence.
 * [vertDrift] gives each bird a tiny independent vertical float
 *   simulating air current variation.
 */
private class Bird(
    var x:             Float,    // normalised 0..1
    var y:             Float,    // base y (normalised), upper sky only
    val speed:         Float,    // horizontal drift per tick (normalised)
    val depth:         BirdDepth,
    val spanScale:     Float,    // wing span multiplier
    val wingAsymmetry: Float,    // left/right span ratio deviation  -0.12..+0.12
    val bodyTilt:      Float,    // subtle body angle offset (radians)
    var bob:           Float,    // vertical bob accumulator (radians)
    val bobRate:       Float,
    val bobAmp:        Float,    // px, scaled by depth at draw time
    var flapPhase:     Float,
    val flapRate:      Float,
    val flapAmp:       Float,    // max wing-tip lift fraction of halfSpan
    val isSoaring:     Boolean,
    var vertDrift:     Float,    // slow vertical float accumulator
    val vertDriftRate: Float,
    val vertDriftAmp:  Float     // px, small vertical air-current float
)

// ─────────────────────────────────────────────────────────────────────────────
// Formation builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds the complete bird list.
 *
 * Flock V-formations have imperfect spacing (small positional jitter applied
 * to each flanker position so the V does not look mathematically exact).
 * Solo soaring birds are added separately.
 */
private fun buildBirds(flockCount: Int, soloCount: Int, rng: Random): List<Bird> = buildList {

    repeat(flockCount) {
        val flockX    = rng.nextFloat()
        val flockY    = rng.nextFloat() * 0.28f + 0.04f   // upper 28% of sky
        val baseSpeed = 0.00014f + rng.nextFloat() * 0.00014f
        val flockSize = if (rng.nextFloat() < 0.55f) 3 else 2

        // Assign a depth to the flock — most flocks are FAR/MID
        val depth = when (rng.nextFloat()) {
            in 0f..0.50f -> BirdDepth.FAR
            in 0f..0.80f -> BirdDepth.MID
            else         -> BirdDepth.NEAR
        }

        for (i in 0 until flockSize) {
            // V formation: leader at (0,0); flankers behind and to the sides
            val side = if (i % 2 == 0) 1f else -1f
            // Imperfect spacing: small random jitter on each flanker
            val spacingJitterX = (rng.nextFloat() - 0.5f) * 0.005f
            val spacingJitterY = (rng.nextFloat() - 0.5f) * 0.003f
            val posX = if (i == 0) 0f else side * i * 0.019f + spacingJitterX
            val posY = if (i == 0) 0f else i * 0.013f + spacingJitterY

            add(Bird(
                x             = (flockX + posX).coerceIn(0f, 1f),
                y             = (flockY + posY).coerceIn(0f, 0.44f),
                speed         = baseSpeed * (0.88f + rng.nextFloat() * 0.24f),
                depth         = depth,
                spanScale     = 0.55f + rng.nextFloat() * 0.50f,
                wingAsymmetry = (rng.nextFloat() - 0.5f) * 0.20f,
                bodyTilt      = (rng.nextFloat() - 0.5f) * 0.10f,
                bob           = rng.nextFloat() * TWO_PI,
                bobRate       = 0.022f + rng.nextFloat() * 0.020f,
                bobAmp        = 0.8f + rng.nextFloat() * 1.4f,
                flapPhase     = rng.nextFloat() * TWO_PI,
                flapRate      = 0.085f + rng.nextFloat() * 0.045f,
                flapAmp       = 0.38f + rng.nextFloat() * 0.20f,
                isSoaring     = false,
                vertDrift     = rng.nextFloat() * TWO_PI,
                vertDriftRate = 0.008f + rng.nextFloat() * 0.008f,
                vertDriftAmp  = 0.6f + rng.nextFloat() * 0.8f
            ))
        }
    }

    // Solo soaring birds — slow lazy flap, slightly larger, slightly more visible
    repeat(soloCount) {
        val depth = if (rng.nextFloat() < 0.40f) BirdDepth.MID else BirdDepth.NEAR
        add(Bird(
            x             = rng.nextFloat(),
            y             = rng.nextFloat() * 0.22f + 0.05f,
            speed         = 0.000085f + rng.nextFloat() * 0.000080f,
            depth         = depth,
            spanScale     = 1.05f + rng.nextFloat() * 0.35f,
            wingAsymmetry = (rng.nextFloat() - 0.5f) * 0.14f,
            bodyTilt      = (rng.nextFloat() - 0.5f) * 0.08f,
            bob           = rng.nextFloat() * TWO_PI,
            bobRate       = 0.012f + rng.nextFloat() * 0.010f,
            bobAmp        = 1.8f + rng.nextFloat() * 1.8f,
            flapPhase     = rng.nextFloat() * TWO_PI,
            flapRate      = 0.028f + rng.nextFloat() * 0.020f,   // very slow soaring flap
            flapAmp       = 0.18f + rng.nextFloat() * 0.12f,     // small amplitude — gliding
            isSoaring     = true,
            vertDrift     = rng.nextFloat() * TWO_PI,
            vertDriftRate = 0.005f + rng.nextFloat() * 0.006f,
            vertDriftAmp  = 1.2f + rng.nextFloat() * 1.4f        // soaring birds drift more
        ))
    }
}

private val TWO_PI = (2f * Math.PI).toFloat()

// ─────────────────────────────────────────────────────────────────────────────
// Bird draw helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws a natural bird silhouette using two quadratic Bézier wing curves.
 *
 * Improvements over the original:
 *
 *  [wingAsymmetry] makes the left and right wings slightly different lengths,
 *  preventing the perfect W-shape that looks like an icon.
 *
 *  The control point is placed at a different fraction for each wing half
 *  (0.52 vs 0.58) adding subtle natural curvature asymmetry.
 *
 *  [bodyTilt] rotates the body dot slightly so the bird reads as turning.
 *
 *  [flapAmp] per bird so wings don't have identical range of motion.
 *
 *  The body is drawn as a tiny elongated oval (not a perfect dot) to suggest
 *  a real bird body without adding obvious geometry.
 *
 *  Depth-scaled span: FAR birds are drawn at 60% of their nominal span,
 *  making them genuinely small rather than just faint.
 */
private fun DrawScope.drawBird(
    center:        Offset,
    halfSpan:      Float,
    flapT:         Float,   // -1..1 wing position
    flapAmp:       Float,   // max fraction of halfSpan for tip lift
    wingAsymmetry: Float,   // left/right span ratio deviation
    color:         Color,
    strokeWidth:   Float
) {
    val tipLift = halfSpan * flapAmp * flapT

    // Left wing — slightly shorter or longer based on asymmetry
    val leftSpan  = halfSpan * (1f - wingAsymmetry)
    val rightSpan = halfSpan * (1f + wingAsymmetry)

    val leftTip   = Offset(center.x - leftSpan,  center.y - tipLift * (1f - wingAsymmetry * 0.5f))
    val rightTip  = Offset(center.x + rightSpan, center.y - tipLift * (1f + wingAsymmetry * 0.5f))

    // Control points: inner arc — slightly different fractions give subtle asymmetry
    val leftMidLift  = halfSpan * 0.16f * flapT
    val rightMidLift = halfSpan * 0.20f * flapT
    val leftCtrl     = Offset(center.x - leftSpan  * 0.52f, center.y - leftMidLift)
    val rightCtrl    = Offset(center.x + rightSpan * 0.58f, center.y - rightMidLift)

    val path = Path().apply {
        moveTo(leftTip.x, leftTip.y)
        quadraticTo(leftCtrl.x, leftCtrl.y, center.x, center.y)
        quadraticTo(rightCtrl.x, rightCtrl.y, rightTip.x, rightTip.y)
    }

    drawPath(
        path  = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Tiny elongated body — short horizontal stroke to suggest bird body
    // rather than a perfect circle dot
    val bodyLen = halfSpan * 0.18f
    drawLine(
        color       = color.copy(alpha = color.alpha * 0.85f),
        start       = Offset(center.x - bodyLen, center.y),
        end         = Offset(center.x + bodyLen, center.y),
        strokeWidth = strokeWidth * 1.6f,
        cap         = StrokeCap.Round
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BirdLayer(theme: AuraTheme) {

    // Birds are only visible during daylight (solar elevation clearly positive)
    // and in calm weather.  solarElev > 0.05 corresponds roughly to morning–afternoon.
    // Use the ONE authoritative solar elevation from the theme rather than polling.
    val solarElev = theme.solarElevNorm

    val showBirds = solarElev > 0.05f &&
        theme.weatherState in listOf(WeatherState.CLEAR, WeatherState.CLOUDY)

    if (!showBirds) return

    val isResumed by rememberIsResumed()
    val wind      = LocalWindIntensity.current

    // Flock and solo counts — season-aware (same logic as before, preserved)
    val flockCount = when (theme.profile.season) {
        Season.AUTUMN -> 3
        Season.SPRING -> 2
        Season.WINTER -> 1
        else          -> 2   // SUMMER
    }
    val soloCount = when (theme.profile.season) {
        Season.AUTUMN -> 2
        Season.SPRING -> 2
        else          -> 1
    }

    val birds = remember(flockCount, soloCount) {
        mutableStateListOf<Bird>().also { list ->
            list.addAll(buildBirds(flockCount, soloCount, Random))
        }
    }

    LaunchedEffect(isResumed, theme.animationsEnabled) {
        if (!isResumed || !theme.animationsEnabled) return@LaunchedEffect
        while (isActive) {
            // Wind adds a slight upward/lateral component to all birds
            val windBoost = wind * 0.000025f
            for (bird in birds) {
                bird.x         -= bird.speed + windBoost
                bird.bob       += bird.bobRate
                bird.flapPhase += bird.flapRate
                bird.vertDrift += bird.vertDriftRate
                // Wrap: birds re-enter from the right when they cross the left edge
                if (bird.x < -0.08f) bird.x = 1.08f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val minDim = size.minDimension

        for (bird in birds) {
            // Depth-scaled span: FAR = 60%, MID = 80%, NEAR = 100%
            val depthSpanScale = when (bird.depth) {
                BirdDepth.FAR  -> 0.60f
                BirdDepth.MID  -> 0.80f
                BirdDepth.NEAR -> 1.00f
            }
            val halfSpan = minDim * 0.012f * bird.spanScale * depthSpanScale

            // Stroke width also depth-scaled
            val strokeW = when (bird.depth) {
                BirdDepth.FAR  -> 0.9f
                BirdDepth.MID  -> 1.3f
                BirdDepth.NEAR -> 1.7f
            }

            val flapT  = sin(bird.flapPhase).toFloat()
            val bobY   = sin(bird.bob) * bird.bobAmp
            val floatY = sin(bird.vertDrift) * bird.vertDriftAmp

            val cx = bird.x * size.width
            val cy = bird.y * size.height + bobY + floatY

            val color = birdColor(solarElev, bird.depth, theme.weatherState)

            drawBird(
                center        = Offset(cx, cy),
                halfSpan      = halfSpan,
                flapT         = flapT,
                flapAmp       = bird.flapAmp,
                wingAsymmetry = bird.wingAsymmetry,
                color         = color,
                strokeWidth   = strokeW
            )
        }
    }
}
