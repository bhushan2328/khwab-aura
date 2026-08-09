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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.CloudStyle
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Photographic atmospheric cloud layer.
 *
 * Six distinct cloud morphologies are generated procedurally:
 *   CUMULUS      — classic wide-base bumpy-top formation
 *   WISPY        — thin high-altitude cirrus-like streaks
 *   LAYERED      — wide flat stratocumulus bands
 *   FRACTURED    — broken, partially dissolved irregular clouds
 *   TOWER        — tall vertically developed cumulonimbus column
 *   SMALL        — compact distant puffs
 *
 * Each cloud is rendered as multiple overlapping Bézier-spline sub-masses
 * (core body + internal lobes + shadow pocket + edge wisps) so the interior
 * has natural density variation rather than being a flat-filled silhouette.
 *
 * Per-vertex alpha variation creates edges that dissolve unevenly into the sky
 * rather than cutting off at a uniform boundary.
 *
 * Lighting is directional:
 *   • Daytime: bright lit crown, graduated shadow underside.
 *   • Sunset: only the top/right-facing surfaces catch warm orange light;
 *     the main body stays neutral-grey so it doesn't look wholly orange.
 *   • Night: low-contrast cool moonlight; clouds are dark with faint silver rims.
 *
 * The existing CloudStyle / TimePhase / wind / depth-plane architecture is
 * completely preserved.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

/** Angular + radial control point for a spline outline. */
private data class CP(val angle: Float, val dist: Float, val alpha: Float = 1f)

/** A sub-mass within a cloud: its own outline, size, and offset from the anchor. */
private data class SubMass(
    val pts: List<CP>,
    val offX: Float,       // offset from cloud anchor, in halfW units (keep small: ≤ 0.4)
    val offY: Float,       // offset from cloud anchor, in halfH units
    val scaleW: Float,     // width multiplier relative to parent halfW
    val scaleH: Float,
    val alphaScale: Float, // opacity multiplier: 0 = ghost, 1 = full; negative = shadow darkening
    val brightBias: Float = 0f,  // +ve = slightly brighter highlight, -ve = slightly darker pocket
    val tension: Float = -1f     // per-mass tension override; -1 = use cloud default
)

/** Cloud morphology type — determines silhouette generation strategy. */
private enum class Morphology {
    CUMULUS, WISPY, LAYERED, FRACTURED, TOWER, SMALL
}

/**
 * One complete organic cloud formation.
 *
 * @param x           Horizontal anchor (normalised 0..1 of canvas width).
 * @param y           Vertical anchor.
 * @param width       Base width fraction of minDimension.
 * @param height      Base height fraction of minDimension.
 * @param masses      Sub-masses drawn back-to-front to build up volume.
 * @param speed       Drift speed per 16 ms tick, before wind scaling.
 * @param depth       0 = far, 1 = mid, 2 = near.
 * @param baseAlpha   Per-cloud opacity factor.
 * @param morphology  Visual formation type.
 */
private class OrganicCloud(
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val masses: List<SubMass>,
    val speed: Float,
    val depth: Int,
    val baseAlpha: Float,
    val morphology: Morphology
)

// ─────────────────────────────────────────────────────────────────────────────
// Spline helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds a closed cubic Bézier path from polar [CP] control points mapped onto
 * an ellipse of half-axes [halfW] × [halfH] centred at ([cx], [cy]).
 *
 * [expandFactor] uniformly scales all radii — used for halo passes.
 * [tension] controls Catmull-Rom smoothness: ~0.25 = tight, ~0.55 = loose.
 * Per-vertex [CP.alpha] values are NOT used here (they drive separate draw calls).
 */
private fun splinePath(
    pts: List<CP>,
    cx: Float,
    cy: Float,
    halfW: Float,
    halfH: Float,
    expandFactor: Float = 1f,
    tension: Float = 0.38f
): Path {
    val n = pts.size
    if (n < 3) return Path()

    fun pt(i: Int): Offset {
        val p = pts[((i % n) + n) % n]
        val r = p.dist * expandFactor
        return Offset(cx + cos(p.angle) * halfW * r, cy + sin(p.angle) * halfH * r)
    }

    val path = Path()
    for (i in 0 until n) {
        val p0 = pt(i - 1); val p1 = pt(i)
        val p2 = pt(i + 1); val p3 = pt(i + 2)
        val cp1x = p1.x + (p2.x - p0.x) * tension / 3f
        val cp1y = p1.y + (p2.y - p0.y) * tension / 3f
        val cp2x = p2.x - (p3.x - p1.x) * tension / 3f
        val cp2y = p2.y - (p3.y - p1.y) * tension / 3f
        if (i == 0) path.moveTo(p1.x, p1.y)
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    path.close()
    return path
}

/**
 * Adds fine-scale noise to each control point's radial distance.
 * [frequency] controls how many noise cycles fit around the circumference.
 * [amplitude] is the maximum fractional change in radius.
 * This breaks the overly-smooth "vector blob" look without disturbing the
 * overall silhouette produced by the outline generators.
 */
private fun List<CP>.withMicroNoise(
    rng: Random,
    amplitude: Float,
    freqScale: Int = 3   // higher = finer wrinkles; 2–5 is photographic range
): List<CP> = mapIndexed { i, cp ->
    // Two noise octaves: coarse shape variation + fine surface texture
    val phase1 = i * freqScale * 0.91f
    val phase2 = i * freqScale * 2.17f + 1.33f
    val noise  = (sin(phase1) * 0.60f + sin(phase2) * 0.40f) *
                 amplitude * (0.70f + rng.nextFloat() * 0.60f)
    cp.copy(dist = (cp.dist + noise).coerceAtLeast(0.05f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Outline generators — one per morphology
// ─────────────────────────────────────────────────────────────────────────────

/** Evenly spaced angles with [jitter] fraction of sector width, offset by [phase]. */
private fun evenAngles(n: Int, jitter: Float, phase: Float, rng: Random): FloatArray =
    FloatArray(n) { i ->
        val base = 2f * PI.toFloat() * i / n + phase
        val j    = (rng.nextFloat() - 0.5f) * jitter * (2f * PI.toFloat() / n)
        ((base + j) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat())
    }.also { it.sort() }

/**
 * Classic cumulus: flat-ish but irregular base, clearly bumpy top, rich internal
 * variation.  Base points have slight downward wisps; top has strong convective towers.
 */
private fun cumulusOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.4f - 0.2f
    val angles = evenAngles(n, 0.55f, phase, rng)
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val dist = when {
            sinA < -0.2f -> {
                val base  = 0.80f + rng.nextFloat() * 0.55f
                val spike = if (rng.nextFloat() < 0.30f) rng.nextFloat() * 0.25f else 0f
                (base + spike).coerceIn(0.60f, 1.50f)
            }
            sinA > 0.30f -> {
                val base = 0.58f + rng.nextFloat() * 0.22f
                val wisp = if (rng.nextFloat() < 0.20f) -(rng.nextFloat() * 0.12f) else 0f
                (base + wisp).coerceIn(0.35f, 0.85f)
            }
            else -> 0.72f + (rng.nextFloat() - 0.5f) * 0.28f
        }
        // Base edges dissolve; crown stays dense
        val edgeAlpha = 1f - (sinA.coerceAtLeast(0f)) * 0.60f
        CP(a, dist, edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.045f, freqScale = 3)
}

/**
 * Wispy cirrus: elongated, extremely thin, very irregular.
 * Many points but small radial variation — creates a fibrous texture.
 */
private fun wispyOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * PI.toFloat() * 2f
    val angles = evenAngles(n, 0.70f, phase, rng)
    return angles.mapIndexed { _, a ->
        val cosA      = cos(a)
        val axialness = abs(cosA)
        val dist      = 0.28f + axialness * 0.72f + (rng.nextFloat() - 0.5f) * 0.40f
        // Wispy edges are always very translucent — tips fade to near-zero
        val tipFade   = 1f - axialness * axialness   // 0 at tips, 1 at waist
        val edgeAlpha = 0.15f + tipFade * 0.55f + rng.nextFloat() * 0.20f
        CP(a, dist.coerceIn(0.10f, 1.30f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.065f, freqScale = 4)  // finer wrinkles for fibrous look
}

/**
 * Stratocumulus layer: wide, flat, low contrast, with undulating top and base.
 * Large width, small height, smooth but not perfectly flat on either edge.
 */
private fun layeredOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.3f
    val angles = evenAngles(n, 0.35f, phase, rng)
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val cosA = cos(a)
        val dist = when {
            sinA < 0f -> 0.74f + rng.nextFloat() * 0.24f
            else      -> 0.60f + rng.nextFloat() * 0.20f
        }
        // Lateral ends of a band dissolve softly
        val latEdge   = abs(cosA)
        val edgeAlpha = (0.85f - latEdge * 0.40f).coerceIn(0.30f, 1.0f)
        CP(a, dist, edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.030f, freqScale = 2)
}

/**
 * Fractured / dissolving cloud: many concavities, broken edges, irregular holes.
 * Some vertices pulled dramatically inward to create gaps in the outline.
 */
private fun fracturedOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 2f * PI.toFloat()
    val angles = evenAngles(n, 0.80f, phase, rng)
    return angles.mapIndexed { _, a ->
        val fractured = rng.nextFloat() < 0.25f
        val dist = if (fractured) {
            0.18f + rng.nextFloat() * 0.28f
        } else {
            0.52f + rng.nextFloat() * 0.55f
        }
        // Fractured concavities are near-zero alpha — they appear as gaps in the cloud
        val edgeAlpha = if (fractured) rng.nextFloat() * 0.25f
                        else 0.45f + rng.nextFloat() * 0.50f
        CP(a, dist.coerceIn(0.08f, 1.20f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.050f, freqScale = 4)
}

/**
 * Tower (cumulonimbus): tall vertically developed, narrow base, dramatic crown.
 * Height > width. Multiple high towers at the crown.
 */
private fun towerOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.4f
    val angles = evenAngles(n, 0.50f, phase, rng)
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val dist = when {
            sinA < -0.50f -> 1.10f + rng.nextFloat() * 0.55f
            sinA < 0f     -> 0.85f + rng.nextFloat() * 0.35f
            sinA > 0.40f  -> 0.42f + rng.nextFloat() * 0.22f
            else          -> 0.68f + (rng.nextFloat() - 0.5f) * 0.25f
        }
        val edgeAlpha = 1f - sinA.coerceAtLeast(0f) * 0.45f
        CP(a, dist.coerceIn(0.30f, 1.60f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.040f, freqScale = 3)
}

/**
 * Small distant puff: compact, roughly rounded but still irregular.
 */
private fun smallOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 2f * PI.toFloat()
    val angles = evenAngles(n, 0.60f, phase, rng)
    return angles.map { a ->
        val sinA = sin(a)
        val dist = 0.62f + (rng.nextFloat() - 0.5f) * 0.42f
        // Small puffs have soft edges all around, slightly softer at base
        val edgeAlpha = 0.50f + rng.nextFloat() * 0.40f - sinA.coerceAtLeast(0f) * 0.25f
        CP(a, dist.coerceIn(0.38f, 1.08f), edgeAlpha.coerceIn(0.20f, 0.95f))
    }.withMicroNoise(rng, amplitude = 0.035f, freqScale = 3)
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-mass builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generates the [SubMass] list for one cloud.
 *
 * Each cloud gets:
 *   1. A primary outer envelope (full width).
 *   2. 1–3 internal lobe masses offset from the centre — creates overlapping density.
 *   3. An optional thin dark shadow pocket near the underside.
 *   4. One or two edge-wisp sub-masses at low alpha for dissolving borders.
 *
 * All masses use the same outline morphology but independent random seeds,
 * so they look related but varied — like overlapping cloud regions.
 */
private fun buildMasses(rng: Random, morphology: Morphology, depth: Int): List<SubMass> {
    val masses = mutableListOf<SubMass>()

    // ── Point-count budget per depth plane ───────────────────────────────────
    val (outerN, lobeN, wispN) = when (depth) {
        0    -> Triple(10, 7,  5)
        1    -> Triple(14, 9,  7)
        else -> Triple(18, 11, 8)
    }

    fun outline(n: Int) = when (morphology) {
        Morphology.CUMULUS   -> cumulusOutline(n, rng)
        Morphology.WISPY     -> wispyOutline(n, rng)
        Morphology.LAYERED   -> layeredOutline(n, rng)
        Morphology.FRACTURED -> fracturedOutline(n, rng)
        Morphology.TOWER     -> towerOutline(n, rng)
        Morphology.SMALL     -> smallOutline(n, rng)
    }

    // ── Primary envelope ─────────────────────────────────────────────────────
    masses += SubMass(
        pts        = outline(outerN),
        offX       = 0f,
        offY       = 0f,
        scaleW     = 1.0f,
        scaleH     = 1.0f,
        alphaScale = 1.0f,
        brightBias = 0f
    )

    // ── Internal lobes — overlapping sub-masses that build up volume ──────────
    // Offsets are kept within ±0.35 halfW/halfH so lobes stay inside the envelope,
    // blending into a continuous volume rather than peeking out as separate shapes.
    val lobeCount = when (depth) { 0 -> 1; 1 -> 2; else -> 3 }
    repeat(lobeCount) { lobeIdx ->
        // Bias toward upper interior — cloud mass reads as lifted
        val offX = (rng.nextFloat() - 0.5f) * 0.35f   // tighter than before
        val offY = -(rng.nextFloat() * 0.25f)           // upward
        val sw   = 0.48f + rng.nextFloat() * 0.30f
        val sh   = 0.42f + rng.nextFloat() * 0.28f
        // Vary brightness: first lobe is brightest inner mass, later lobes darker
        val bright = 0.08f - lobeIdx * 0.06f
        masses += SubMass(
            pts        = outline(lobeN),
            offX       = offX,
            offY       = offY,
            scaleW     = sw,
            scaleH     = sh,
            alphaScale = 0.42f + rng.nextFloat() * 0.28f,
            brightBias = bright,
            tension    = -1f   // lobes get slightly looser tension in renderer
        )
    }

    // ── Shadow pocket — broad soft darkening at the underside ─────────────────
    // Uses a large scaleW (close to envelope width) so the shadow blends seamlessly
    // into the base rather than appearing as a visible dark stripe.
    if (morphology in listOf(Morphology.CUMULUS, Morphology.TOWER, Morphology.LAYERED)) {
        masses += SubMass(
            pts        = layeredOutline(lobeN, rng),
            offX       = (rng.nextFloat() - 0.5f) * 0.20f,   // less lateral offset
            offY       = 0.18f + rng.nextFloat() * 0.18f,
            scaleW     = 0.82f + rng.nextFloat() * 0.16f,    // wide — covers base
            scaleH     = 0.28f + rng.nextFloat() * 0.16f,
            alphaScale = -(0.12f + rng.nextFloat() * 0.16f)  // negative = darkening pass
        )
    }

    // ── Edge wisps — wispy tendrils that taper and dissolve at the margins ────
    val wispCount = if (depth == 0) 0 else if (depth == 1) 1 else rng.nextInt(1, 3)
    repeat(wispCount) {
        val side = if (rng.nextFloat() < 0.5f) -1f else 1f
        // Wisps originate near the cloud edge (0.45–0.70 halfW) and extend outward
        val offX = side * (0.45f + rng.nextFloat() * 0.30f)
        val offY = (rng.nextFloat() - 0.6f) * 0.30f   // slightly higher than centre
        masses += SubMass(
            pts        = wispyOutline(wispN, rng),
            offX       = offX,
            offY       = offY,
            scaleW     = 0.28f + rng.nextFloat() * 0.26f,
            scaleH     = 0.12f + rng.nextFloat() * 0.12f,
            alphaScale = 0.14f + rng.nextFloat() * 0.18f,
            brightBias = -0.05f   // wisps are slightly cooler/darker than core
        )
    }

    return masses
}

// ─────────────────────────────────────────────────────────────────────────────
// Cloud factory
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selects a [Morphology] appropriate to the depth and some random variation.
 * Deep (far) clouds are small or wispy; near clouds can be any type.
 */
private fun pickMorphology(rng: Random, depth: Int): Morphology = when (depth) {
    0 -> when (rng.nextInt(4)) {
        0    -> Morphology.WISPY
        1    -> Morphology.SMALL
        2    -> Morphology.LAYERED
        else -> Morphology.SMALL
    }
    1 -> when (rng.nextInt(5)) {
        0    -> Morphology.CUMULUS
        1    -> Morphology.LAYERED
        2    -> Morphology.FRACTURED
        3    -> Morphology.WISPY
        else -> Morphology.CUMULUS
    }
    else -> when (rng.nextInt(6)) {
        0    -> Morphology.CUMULUS
        1    -> Morphology.TOWER
        2    -> Morphology.LAYERED
        3    -> Morphology.FRACTURED
        4    -> Morphology.CUMULUS
        else -> Morphology.CUMULUS
    }
}

private fun buildOrganicCloud(rng: Random, depth: Int): OrganicCloud {

    val morphology = pickMorphology(rng, depth)

    // ── Size — varies significantly; near clouds can be very large ────────────
    val widthBase = when (depth) {
        0 -> when (morphology) {
            Morphology.WISPY   -> rng.nextFloat() * 0.12f + 0.10f
            Morphology.SMALL   -> rng.nextFloat() * 0.05f + 0.06f
            else               -> rng.nextFloat() * 0.08f + 0.10f
        }
        1 -> when (morphology) {
            Morphology.LAYERED -> rng.nextFloat() * 0.18f + 0.22f
            Morphology.WISPY   -> rng.nextFloat() * 0.14f + 0.16f
            else               -> rng.nextFloat() * 0.12f + 0.16f
        }
        else -> when (morphology) {
            Morphology.TOWER   -> rng.nextFloat() * 0.10f + 0.16f
            Morphology.LAYERED -> rng.nextFloat() * 0.24f + 0.28f
            else               -> rng.nextFloat() * 0.16f + 0.20f
        }
    }

    // Aspect ratio: varies strongly by morphology
    val aspectRatio = when (morphology) {
        Morphology.WISPY   -> 0.12f + rng.nextFloat() * 0.10f   // extremely flat
        Morphology.LAYERED -> 0.20f + rng.nextFloat() * 0.12f   // flat band
        Morphology.TOWER   -> 1.40f + rng.nextFloat() * 0.60f   // taller than wide
        Morphology.SMALL   -> 0.60f + rng.nextFloat() * 0.30f   // roughly round
        else               -> 0.38f + rng.nextFloat() * 0.22f   // moderate
    }
    val heightBase = widthBase * aspectRatio

    // ── Base opacity by depth ──────────────────────────────────────────────────
    val baseAlpha = when (depth) {
        0    -> rng.nextFloat() * 0.10f + 0.22f   // hazy
        1    -> rng.nextFloat() * 0.14f + 0.36f
        else -> rng.nextFloat() * 0.14f + 0.48f
    }

    // ── Position ───────────────────────────────────────────────────────────────
    val x = rng.nextFloat()
    val y = when (depth) {
        0    -> rng.nextFloat() * 0.22f + 0.02f
        1    -> rng.nextFloat() * 0.25f + 0.04f
        else -> rng.nextFloat() * 0.22f + 0.08f
    }

    // ── Drift speed ────────────────────────────────────────────────────────────
    val speedBase = when (depth) {
        0    -> 0.000024f
        1    -> 0.000050f
        else -> 0.000090f
    }
    // Wispy clouds travel slightly faster at any depth (high-altitude jet stream feel)
    val morphSpeed = if (morphology == Morphology.WISPY) 1.30f else 1.0f
    val speed = (speedBase + rng.nextFloat() * speedBase * 0.80f) * morphSpeed

    return OrganicCloud(
        x          = x,
        y          = y,
        width      = widthBase,
        height     = heightBase,
        masses     = buildMasses(rng, morphology, depth),
        speed      = speed,
        depth      = depth,
        baseAlpha  = baseAlpha,
        morphology = morphology
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Renderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws one [OrganicCloud] with full layered, internally structured rendering.
 *
 * Lighting model:
 *   [litColor]    — the colour of sunlit surfaces (top/upper face).
 *   [shadowColor] — the colour of shadowed undersides.
 *   [midColor]    — an intermediate neutral tone for the main body mass.
 *   [rimColor]    — a directional edge highlight used at sunset.
 *
 * For each [SubMass]:
 *   • If alphaScale is positive → it's a cloud lobe drawn with a vertical
 *     gradient from [litColor] at top to [shadowColor] at bottom.
 *   • If alphaScale is negative → it's a shadow darkening pass drawn
 *     with a semi-transparent [shadowColor] fill.
 *
 * Each mass is drawn in three expansion passes (halo → mid → core) so edges
 * dissolve naturally without a hard boundary.
 */
private fun DrawScope.drawOrganicCloud(
    cloud: OrganicCloud,
    litColor:     Color,
    shadowColor:  Color,
    midColor:     Color,
    rimColor:     Color,
    ambientAlpha: Float,
    isSunset:     Boolean,
    isNight:      Boolean,
    depthFade:    Float    // 0 = near (no fade), 1 = far (max atmospheric fade)
) {
    val cxPx  = cloud.x * size.width
    val cyPx  = cloud.y * size.height
    val halfW = cloud.width  * size.minDimension * 0.5f
    val halfH = cloud.height * size.minDimension * 0.5f
    val base  = cloud.baseAlpha * ambientAlpha

    // Atmospheric perspective: far clouds lose contrast and colour saturation.
    // We interpolate lit and shadow both toward a neutral mid-grey with distance.
    val atmMid   = Color(0.72f, 0.74f, 0.76f)  // neutral sky-grey for far blending
    val atmBlend = depthFade * 0.38f            // max 38% blend toward atmospheric grey
    fun atmMix(c: Color) = Color(
        red   = c.red   * (1f - atmBlend) + atmMid.red   * atmBlend,
        green = c.green * (1f - atmBlend) + atmMid.green * atmBlend,
        blue  = c.blue  * (1f - atmBlend) + atmMid.blue  * atmBlend
    )
    val atmLit    = atmMix(litColor)
    val atmShadow = atmMix(shadowColor)
    val atmMid2   = atmMix(midColor)
    val atmRim    = atmMix(rimColor)

    // Base spline tension by morphology
    val baseTension = when (cloud.morphology) {
        Morphology.WISPY     -> 0.27f
        Morphology.FRACTURED -> 0.29f
        Morphology.LAYERED   -> 0.31f
        Morphology.SMALL     -> 0.34f
        else                 -> 0.41f
    }

    for (mass in cloud.masses) {
        val isShadowMass = mass.alphaScale < 0f
        val mAlpha       = if (isShadowMass) abs(mass.alphaScale) else mass.alphaScale

        // Lobes (non-shadow masses with a nonzero offset) get slightly looser spline
        // tension so they blend softly into the primary envelope rather than sitting
        // as visibly distinct shapes.
        val isLobe   = !isShadowMass && (mass.offX != 0f || mass.offY != 0f)
        val mTension = if (mass.tension >= 0f) mass.tension
                       else if (isLobe) baseTension + 0.05f
                       else baseTension

        val mCx = cxPx  + mass.offX * halfW * 2f
        val mCy = cyPx  + mass.offY * halfH * 2f
        val mHW = halfW * mass.scaleW
        val mHH = halfH * mass.scaleH

        // Bounding box for gradient anchoring — extra headroom on top for spiky crowns
        val massTop    = mCy - mHH * 1.40f
        val massBottom = mCy + mHH * 0.80f

        // ── Shadow darkening pass (negative alphaScale masses) ─────────────
        if (isShadowMass) {
            // Shadow fades in slowly from the top, peaks near the base centre,
            // then fades out at the very bottom — avoids a visible stripe boundary.
            val shadowBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to atmShadow.copy(alpha = 0f),
                    0.20f to atmShadow.copy(alpha = base * mAlpha * 0.28f),
                    0.50f to atmShadow.copy(alpha = base * mAlpha * 0.58f),
                    0.78f to atmShadow.copy(alpha = base * mAlpha * 0.45f),
                    1.00f to atmShadow.copy(alpha = 0f)
                ),
                startY = massTop,
                endY   = massBottom
            )
            // Draw with slight halo expansion so the shadow softly bleeds into the cloud body
            drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.08f, mTension), shadowBrush)
            drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.00f, mTension), shadowBrush)
            continue
        }

        // ── Effective lit/shadow colours per time mode ─────────────────────
        val effLit: Color
        val effShadow: Color
        if (isNight) {
            effLit    = atmLit
            effShadow = atmShadow
        } else if (isSunset) {
            // At sunset the lit face blends the neutral body with a warm tint
            val warmBlend = 0.45f
            effLit = Color(
                red   = atmLit.red   * (1f - warmBlend) + atmRim.red   * warmBlend,
                green = atmLit.green * (1f - warmBlend) + atmRim.green * warmBlend,
                blue  = atmLit.blue  * (1f - warmBlend) + atmRim.blue  * warmBlend
            )
            effShadow = atmShadow
        } else {
            effLit    = atmLit
            effShadow = atmShadow
        }

        // Brightness bias: inner bright lobes are slightly lighter at the crown;
        // wisp masses and dark pockets are slightly dimmer.
        val brightAdj = (1f + mass.brightBias).coerceIn(0.70f, 1.25f)

        // Cap the effective lit alpha so we never approach pure white.
        // Real clouds top out around 90–92% white even at noon.
        val litCap = if (isNight) 0.60f else 0.90f

        // ── Per-mass gradient ──────────────────────────────────────────────
        // Seven stops: thin vapour at top → bright lit face → warm-white → neutral mid
        //            → cool grey → dark shadow → fade to transparent at base.
        // This range (bright white → warm → neutral → cool grey → dark) is exactly what
        // you see in real cumulus photography and avoids a uniform white fill.
        fun massGrad(alphaMulti: Float): Brush {
            val a = base * mAlpha * alphaMulti * brightAdj
            return Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to effLit.copy(alpha    = (a * 0.55f).coerceAtMost(litCap * alphaMulti)),
                    0.12f to effLit.copy(alpha    = (a * 0.88f).coerceAtMost(litCap * alphaMulti)),
                    0.28f to effLit.copy(alpha    = (a * 0.95f).coerceAtMost(litCap * alphaMulti)),
                    0.48f to atmMid2.copy(alpha   = a * 0.88f),
                    0.65f to effShadow.copy(alpha = a * 0.72f),
                    0.82f to effShadow.copy(alpha = a * 0.42f),
                    1.00f to effShadow.copy(alpha = 0f)
                ),
                startY = massTop,
                endY   = massBottom
            )
        }

        // ── Expansion passes: halo → vapour fringe → core ─────────────────
        // The halo expansions are not uniform rings — they use progressively lower
        // opacity so the cloud dissolves smoothly without a visible concentric glow.
        // Pass 1: wide outer vapour fringe — very low alpha, irregular dissolve
        drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.38f, mTension), massGrad(0.10f))
        // Pass 2: soft atmospheric edge
        drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.20f, mTension), massGrad(0.22f))
        // Pass 3: mid boundary — where most edge softness lives
        drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.08f, mTension), massGrad(0.44f))
        // Pass 4: core body — full density
        drawPath(splinePath(mass.pts, mCx, mCy, mHW, mHH, 1.00f, mTension), massGrad(0.82f))

        // ── Crown highlight — lit upper face only ──────────────────────────
        // Applied to an inset sub-path so it brightens only the convex crown area,
        // not the entire silhouette.  The highlight fades before the cloud midpoint
        // so it doesn't look like a painted stripe.
        if (!isNight) {
            val rimA     = if (isSunset) base * mAlpha * 0.48f else base * mAlpha * 0.28f
            // Crown highlight ends at ~30% of the way down the mass
            val crownEnd = massTop + (massBottom - massTop) * 0.30f
            drawPath(
                // Inset path: 0.72 expand on height so only the top dome is covered
                splinePath(mass.pts, mCx, mCy, mHW, mHH * 0.72f, 0.80f, mTension),
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to atmRim.copy(alpha = rimA * 0.85f),
                        0.40f to atmRim.copy(alpha = rimA * 0.40f),
                        0.70f to atmRim.copy(alpha = rimA * 0.10f),
                        1.00f to atmRim.copy(alpha = 0f)
                    ),
                    startY = massTop,
                    endY   = crownEnd
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CloudLayer(theme: AuraTheme) {

    val style = theme.profile.clouds
    if (style == CloudStyle.CLEAR) return

    val isResumed by rememberIsResumed()

    // Cloud count per depth plane
    val (farCount, midCount, nearCount) = when (style) {
        CloudStyle.CLEAR     -> Triple(0, 0, 0)
        CloudStyle.FEW       -> Triple(1, 1, 1)
        CloudStyle.SCATTERED -> Triple(2, 2, 1)
        CloudStyle.BROKEN    -> Triple(3, 2, 2)
        CloudStyle.OVERCAST  -> Triple(4, 3, 2)
        CloudStyle.STORM     -> Triple(5, 4, 3)
    }

    val currentHour = remember { TimeState.now().hour + TimeState.now().minute / 60f }

    val isSunset = theme.timePhase == TimePhase.SUNSET
    val isNight  = theme.timePhase == TimePhase.NIGHT || theme.timePhase == TimePhase.MIDNIGHT
    val isStorm  = style == CloudStyle.STORM
    val isOvercast = style == CloudStyle.OVERCAST

    // ── Colour palette ────────────────────────────────────────────────────────

    // litColor: the sunlit upper-face colour
    val litColor: Color = when {
        isStorm    -> Color(0xFF7A7A7A)
        isOvercast -> Color(0xFFD2D2D2)
        else -> when (theme.timePhase) {
            TimePhase.PRE_DAWN  -> Color(0xFF9FB3C8)
            TimePhase.SUNRISE   -> Color(0xFFFFF0E8)    // almost white, faint warm tint
            TimePhase.MORNING   -> {
                val t = ((currentHour - 6f) / 4f).coerceIn(0f, 1f)
                Color(1f, 0.82f + 0.18f * t, 0.74f + 0.26f * t)
            }
            TimePhase.NOON      -> Color(0xFFF9F9F9)
            TimePhase.AFTERNOON -> Color(0xFFFFF8E8)
            TimePhase.SUNSET    -> Color(0xFFE8E0D8)    // neutral grey-white; rim handles the orange
            TimePhase.EVENING   -> Color(0xFFCDD4F0)
            TimePhase.NIGHT,
            TimePhase.MIDNIGHT  -> Color(0xFF8898AA)    // dim moonlit blue-grey
        }
    }

    // shadowColor: underside shadow tone — always darker and desaturated
    val shadowColor: Color = when {
        isStorm    -> Color(0xFF3C3C46)
        isOvercast -> Color(0xFF8E8E96)
        else -> when (theme.timePhase) {
            TimePhase.NIGHT,
            TimePhase.MIDNIGHT -> Color(0xFF3A4050)   // very dark, near-black
            TimePhase.SUNSET   -> Color(0xFF606070)   // cool grey-blue underside
            else -> Color(
                red   = (litColor.red   * 0.48f).coerceIn(0f, 1f),
                green = (litColor.green * 0.50f).coerceIn(0f, 1f),
                blue  = (litColor.blue  * 0.56f).coerceIn(0f, 1f)
            )
        }
    }

    // midColor: the neutral interior body between lit and shadow
    val midColor: Color = Color(
        red   = (litColor.red   * 0.72f + shadowColor.red   * 0.28f).coerceIn(0f, 1f),
        green = (litColor.green * 0.72f + shadowColor.green * 0.28f).coerceIn(0f, 1f),
        blue  = (litColor.blue  * 0.72f + shadowColor.blue  * 0.28f).coerceIn(0f, 1f)
    )

    // rimColor: directional edge highlight
    //   Sunset  → warm orange
    //   Day     → near-white
    //   Night   → faint silver
    val rimColor: Color = when {
        isStorm  -> Color(0xFF6A6A72)
        isSunset -> Color(0xFFFF8C50)
        isNight  -> Color(0xFFB0BCCC)
        else -> when (theme.timePhase) {
            TimePhase.SUNRISE   -> Color(0xFFFFD0A0)
            TimePhase.AFTERNOON -> Color(0xFFFFF4D0)
            else                -> Color(0xFFF8F8F8)
        }
    }

    // Scene-level opacity multiplier
    val sceneAlpha = when (style) {
        CloudStyle.STORM     -> 1.00f
        CloudStyle.OVERCAST  -> 0.94f
        CloudStyle.BROKEN    -> 0.84f
        CloudStyle.SCATTERED -> 0.78f
        CloudStyle.FEW       -> 0.72f
        CloudStyle.CLEAR     -> 0f
    }

    // ── Build clouds once per style ────────────────────────────────────────────
    val rng    = remember(style) { Random(style.ordinal * 3571L + 37L) }
    val clouds = remember(style) {
        mutableStateListOf<OrganicCloud>().also { list ->
            fun addPlane(count: Int, depth: Int) =
                repeat(count) { list += buildOrganicCloud(rng, depth) }
            addPlane(farCount,  0)
            addPlane(midCount,  1)
            addPlane(nearCount, 2)
        }
    }

    val windIntensity = LocalWindIntensity.current

    LaunchedEffect(style, isResumed, theme.animationsEnabled) {
        if (!isResumed || !theme.animationsEnabled) return@LaunchedEffect
        while (isActive) {
            val baseWind = 1f + windIntensity * 1.6f
            for (cloud in clouds) {
                val depthWind = baseWind * (0.52f + cloud.depth * 0.25f)
                cloud.x -= cloud.speed * depthWind
                if (cloud.x < -0.50f) cloud.x = 1.50f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw back-to-front so far-plane clouds sit behind near-plane ones.
        // depthFade: depth 0 (far) = 1.0 (max atmospheric haze), depth 2 (near) = 0.0
        for (depthPass in 0..2) {
            val depthFade = (2 - depthPass) / 2f   // 1.0, 0.5, 0.0
            for (cloud in clouds) {
                if (cloud.depth != depthPass) continue
                drawOrganicCloud(
                    cloud        = cloud,
                    litColor     = litColor,
                    shadowColor  = shadowColor,
                    midColor     = midColor,
                    rimColor     = rimColor,
                    ambientAlpha = sceneAlpha,
                    isSunset     = isSunset,
                    isNight      = isNight,
                    depthFade    = depthFade
                )
            }
        }
    }
}
