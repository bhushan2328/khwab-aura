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
import androidx.compose.ui.graphics.drawscope.withTransform
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.CloudStyle
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Photorealistic atmospheric cloud layer.
 *
 * ── Architecture overview ────────────────────────────────────────────────────
 *
 * Six cloud morphologies:
 *   CUMULUS      — convective fair-weather cloud with asymmetric towers
 *   WISPY        — thin high-altitude cirrus with multi-strand dissolve
 *   LAYERED      — broad stratocumulus deck with undulating base
 *   FRACTURED    — partially dissolved cloud fragments with gap transparency
 *   TOWER        — vertically developed cumulonimbus formation
 *   SMALL        — distant small puffs with strong atmospheric perspective
 *
 * Each cloud contains:
 *   • Primary outer envelope
 *   • 1–4 internal lobe masses offset into the interior
 *   • An optional shadow pocket at the underside
 *   • 0–2 edge-wisp tendrils dissolving at the margins
 *
 * ── Performance: geometry caching ────────────────────────────────────────────
 *
 * Spline paths are baked ONCE at unit scale (halfW=1, halfH=1, cx=0, cy=0)
 * into [CachedMassPaths] when the cloud is first constructed.  At draw time,
 * the canvas is scaled and translated — no Path allocation per frame.
 *
 * Each [CachedMassPaths] stores four expansion levels (1.38, 1.20, 1.08, 1.00)
 * plus a crown sub-path.  The shadow mass stores two paths.
 *
 * ── Performance: Brush allocation ────────────────────────────────────────────
 *
 * Gradient Brush objects are built in [drawOrganicCloud] from colour + alpha
 * parameters that are stable across frames (lighting colours change only at
 * phase transitions, not every frame).  Brushes are NOT cached as class fields
 * to avoid lifecycle coupling, but are built per-render-call only when the
 * canvas's DrawScope is active — identical to how other Compose Canvas work
 * is performed.  This eliminates the previous per-frame allocation inside
 * massGrad() which was called 4 × N_masses × N_clouds per frame.
 *
 * ── Per-vertex alpha ─────────────────────────────────────────────────────────
 *
 * Each outline [CP] carries an [alpha] value.  The mean edge alpha of all CPs
 * in a mass is computed at build time and stored in [CachedMassPaths.edgeAlpha].
 * This drives a separate very-low-opacity halo pass so edges with dissolving
 * alpha values actually appear translucent.  The alpha is no longer ignored.
 *
 * ── Variety / seed ───────────────────────────────────────────────────────────
 *
 * Each cloud uses a unique per-cloud seed:
 *   seed = nanoTime XOR (depth * 13337L) XOR (cloudIndex * 7919L) XOR (style.ordinal * 3571L)
 * This guarantees different geometry for every cloud instance and every session,
 * while remaining stable for the lifetime of one OrganicCloud object.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────

/** Angular + radial control point.  [alpha] drives edge dissolve. */
private data class CP(val angle: Float, val dist: Float, val alpha: Float = 1f)

/** Cloud morphology type. */
private enum class Morphology {
    CUMULUS, WISPY, LAYERED, FRACTURED, TOWER, SMALL
}

/**
 * Pre-baked unit-scale paths for one sub-mass.
 *
 * All paths are computed at halfW=1, halfH=1, cx=0, cy=0.
 * The draw function applies scale+translate to match the actual cloud size.
 *
 * @param expansionPaths  Four paths at expand factors: 1.38, 1.20, 1.08, 1.00.
 * @param crownPath       Inset crown highlight path (expand ~0.80 × height 0.72).
 * @param shadowPaths     Two paths for shadow darkening passes (expand 1.08, 1.00).
 *                        Null for non-shadow masses.
 * @param edgeAlpha       Mean CP.alpha across all outline points — used for the
 *                        outermost halo pass to honour per-vertex dissolve intent.
 * @param offX            X offset from cloud anchor in halfW units.
 * @param offY            Y offset from cloud anchor in halfH units.
 * @param scaleW          Width scale relative to cloud halfW.
 * @param scaleH          Height scale relative to cloud halfH.
 * @param alphaScale      Positive = cloud lobe opacity; negative = shadow darkening.
 * @param brightBias      Brightness adjustment for lit colour.
 * @param isShadow        True when this is the underside shadow darkening mass.
 */
private class CachedMassPaths(
    val expansionPaths: Array<Path>,   // [0]=1.38 [1]=1.20 [2]=1.08 [3]=1.00
    val crownPath:      Path?,
    val shadowPaths:    Array<Path>?,  // [0]=1.08 [1]=1.00 — only for shadow masses
    val edgeAlpha:      Float,
    val offX:           Float,
    val offY:           Float,
    val scaleW:         Float,
    val scaleH:         Float,
    val alphaScale:     Float,
    val brightBias:     Float,
    val isShadow:       Boolean
)

/**
 * One complete organic cloud formation.
 *
 * @param x          Horizontal anchor (normalised 0..1 of canvas width).
 * @param y          Vertical anchor (normalised 0..1 of canvas height).
 * @param width      Base width as a fraction of canvas minDimension.
 * @param height     Base height as a fraction of canvas minDimension.
 * @param masses     Pre-baked geometry for all sub-masses.
 * @param speed      Drift speed per 16 ms tick before wind scaling.
 * @param depth      0 = far, 1 = mid, 2 = near.
 * @param baseAlpha  Per-cloud opacity factor (0..1).
 * @param morphology Visual formation type (affects lighting decisions).
 */
private class OrganicCloud(
    var x:            Float,
    val y:            Float,
    val width:        Float,
    val height:       Float,
    val masses:       List<CachedMassPaths>,
    val speed:        Float,
    val depth:        Int,
    val baseAlpha:    Float,
    val morphology:   Morphology
)

// ─────────────────────────────────────────────────────────────────────────────
// Spline builder — unit scale, call once at construction time
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds a closed cubic Bézier path from polar [CP] control points at
 * unit scale (halfW=1, halfH=1, cx=0, cy=0).
 *
 * [expandFactor] uniformly scales all radii (used for halo/vapour passes).
 * [tension] controls Catmull-Rom smoothness: ~0.25 = tight, ~0.55 = loose.
 */
private fun unitSplinePath(
    pts:          List<CP>,
    expandFactor: Float = 1f,
    tension:      Float = 0.38f
): Path {
    val n = pts.size
    if (n < 3) return Path()

    fun pt(i: Int): Offset {
        val p = pts[((i % n) + n) % n]
        val r = p.dist * expandFactor
        return Offset(cos(p.angle) * r, sin(p.angle) * r)
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

// ─────────────────────────────────────────────────────────────────────────────
// Micro-noise — breaks smooth "vector blob" look
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adds two-octave sinusoidal noise to each CP's radial distance.
 * Higher [freqScale] = finer wrinkles. Range 2–5 covers photographic textures.
 */
private fun List<CP>.withMicroNoise(
    rng:       Random,
    amplitude: Float,
    freqScale: Int = 3
): List<CP> = mapIndexed { i, cp ->
    val phase1 = i * freqScale * 0.91f
    val phase2 = i * freqScale * 2.17f + 1.33f
    val noise  = (sin(phase1) * 0.60f + sin(phase2) * 0.40f) *
                 amplitude * (0.70f + rng.nextFloat() * 0.60f)
    cp.copy(dist = (cp.dist + noise).coerceAtLeast(0.05f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Outline generators — one per morphology
// ─────────────────────────────────────────────────────────────────────────────

/** Evenly spaced angles with [jitter] fraction randomisation, sorted ascending. */
private fun evenAngles(n: Int, jitter: Float, phase: Float, rng: Random): FloatArray =
    FloatArray(n) { i ->
        val base = 2f * PI.toFloat() * i / n + phase
        val j    = (rng.nextFloat() - 0.5f) * jitter * (2f * PI.toFloat() / n)
        ((base + j) % (2f * PI.toFloat()) + 2f * PI.toFloat()) % (2f * PI.toFloat())
    }.also { it.sort() }

/**
 * CUMULUS — irregular wide base, multi-height asymmetric convective towers.
 *
 * The top half of the outline (sinA < 0) gets towers of varying height: some
 * tall, some short, driven by a per-vertex random roll.  This produces genuine
 * asymmetry rather than uniform bumps.  The base (sinA > 0) is flatter but
 * not mathematically flat — subtle unevenness with occasional soft concavities.
 */
private fun cumulusOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.5f - 0.25f
    val angles = evenAngles(n, 0.55f, phase, rng)

    // Assign one of three tower heights randomly to the top half
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val dist = when {
            sinA < -0.55f -> {
                // Strong convective crown zone — varying tower heights
                val towerClass = rng.nextFloat()
                when {
                    towerClass < 0.25f -> 1.30f + rng.nextFloat() * 0.28f  // tall tower
                    towerClass < 0.55f -> 0.95f + rng.nextFloat() * 0.30f  // medium lobe
                    else               -> 0.68f + rng.nextFloat() * 0.22f  // shorter saddle
                }
            }
            sinA < -0.10f -> {
                // Upper lateral region — moderate irregular
                0.80f + (rng.nextFloat() - 0.5f) * 0.32f
            }
            sinA > 0.55f -> {
                // Base — flattish but uneven; rare soft hanging wisps
                val base = 0.60f + rng.nextFloat() * 0.18f
                val concavity = if (rng.nextFloat() < 0.12f) -(rng.nextFloat() * 0.15f) else 0f
                (base + concavity).coerceIn(0.38f, 0.82f)
            }
            sinA > 0.15f -> {
                // Lower lateral — gentle slope between crown and base
                0.68f + (rng.nextFloat() - 0.5f) * 0.22f
            }
            else -> 0.75f + (rng.nextFloat() - 0.5f) * 0.26f
        }
        // Alpha: crown is dense; base edges dissolve; laterals fade softly
        val edgeAlpha = when {
            sinA < -0.40f -> 0.88f + rng.nextFloat() * 0.12f    // crown — dense
            sinA >  0.50f -> 0.30f + rng.nextFloat() * 0.35f    // base — dissolving
            else          -> 0.55f + rng.nextFloat() * 0.35f    // sides
        }
        CP(a, dist.coerceIn(0.35f, 1.58f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.048f, freqScale = 3)
}

/**
 * WISPY — thin high-altitude cirrus.
 *
 * Extremely elongated (aspect ratio handled in size code).
 * Very low alpha everywhere; tips approach zero.
 * Fine-scale noise creates fibre texture.
 */
private fun wispyOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * PI.toFloat() * 2f
    val angles = evenAngles(n, 0.72f, phase, rng)
    return angles.mapIndexed { _, a ->
        val cosA      = cos(a)
        val axialness = abs(cosA)
        // Elongated: large radius near the axis (horizontal extent), small perpendicular
        val dist      = 0.22f + axialness * 0.78f + (rng.nextFloat() - 0.5f) * 0.42f
        // Tips almost invisible; waist moderately visible
        val tipFade   = 1f - axialness * axialness
        val edgeAlpha = (0.08f + tipFade * 0.52f + rng.nextFloat() * 0.18f).coerceIn(0.04f, 0.72f)
        CP(a, dist.coerceIn(0.08f, 1.32f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.072f, freqScale = 5)
}

/**
 * LAYERED — stratocumulus deck.
 *
 * Wide, low, with gentle undulation on both top and base.
 * Lateral ends dissolve softly.  Bottom is not perfectly flat.
 */
private fun layeredOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.4f
    val angles = evenAngles(n, 0.38f, phase, rng)
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val cosA = cos(a)
        // Top undulates: wave amplitude proportional to distance from ends
        val lateralCentredness = 1f - abs(cosA)
        val undulation = if (sinA < 0f)
            lateralCentredness * rng.nextFloat() * 0.22f else 0f
        val dist = when {
            sinA < 0f -> 0.72f + undulation + rng.nextFloat() * 0.14f
            else      -> 0.58f + rng.nextFloat() * 0.16f
        }
        // Lateral ends dissolve; vertical edges are soft
        val edgeAlpha = (0.82f - abs(cosA) * 0.46f + (rng.nextFloat() - 0.5f) * 0.10f)
            .coerceIn(0.28f, 1.0f)
        CP(a, dist.coerceIn(0.38f, 1.10f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.032f, freqScale = 2)
}

/**
 * FRACTURED — broken/dissolving cloud mass.
 *
 * Some vertices are pulled strongly inward creating concavities.
 * Those same vertices get near-zero alpha — the gap dissolves rather than cuts.
 */
private fun fracturedOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 2f * PI.toFloat()
    val angles = evenAngles(n, 0.82f, phase, rng)
    return angles.mapIndexed { _, a ->
        val roll = rng.nextFloat()
        // ~22% vertices pulled inward to create gap/concavity
        val isConcave = roll < 0.22f
        val dist = if (isConcave) {
            0.14f + rng.nextFloat() * 0.22f    // deep inward pull
        } else {
            0.50f + rng.nextFloat() * 0.58f
        }
        val edgeAlpha = if (isConcave) {
            rng.nextFloat() * 0.18f            // near-zero — gap dissolves to sky
        } else {
            0.40f + rng.nextFloat() * 0.50f
        }
        CP(a, dist.coerceIn(0.06f, 1.22f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.055f, freqScale = 4)
}

/**
 * TOWER — cumulonimbus vertical development.
 *
 * Tall narrow with explosive upper crown, narrower mid, broad dark base.
 * Crown gets multiple irregular high-reaching protrusions.
 */
private fun towerOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 0.45f
    val angles = evenAngles(n, 0.52f, phase, rng)
    return angles.mapIndexed { _, a ->
        val sinA = sin(a)
        val dist = when {
            sinA < -0.65f -> {
                // Explosive crown — irregular anvil/tower protrusions
                val towerH = rng.nextFloat()
                when {
                    towerH < 0.30f -> 1.40f + rng.nextFloat() * 0.40f  // main tower
                    towerH < 0.60f -> 1.05f + rng.nextFloat() * 0.30f  // secondary
                    else           -> 0.75f + rng.nextFloat() * 0.25f  // shoulder
                }
            }
            sinA < -0.20f -> 0.82f + rng.nextFloat() * 0.32f           // mid body
            sinA >  0.45f -> 0.48f + rng.nextFloat() * 0.20f           // narrow base
            else          -> 0.68f + (rng.nextFloat() - 0.5f) * 0.28f
        }
        val edgeAlpha = when {
            sinA < -0.50f -> 0.82f + rng.nextFloat() * 0.18f    // crown — dense
            sinA >  0.40f -> 0.40f + rng.nextFloat() * 0.30f    // base — softer
            else          -> 0.60f + rng.nextFloat() * 0.30f
        }
        CP(a, dist.coerceIn(0.28f, 1.80f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.042f, freqScale = 3)
}

/**
 * SMALL — distant puff.
 *
 * Compact, moderately rounded, very soft edges.
 * Lower detail than near-plane clouds.
 */
private fun smallOutline(n: Int, rng: Random): List<CP> {
    val phase  = rng.nextFloat() * 2f * PI.toFloat()
    val angles = evenAngles(n, 0.62f, phase, rng)
    return angles.map { a ->
        val sinA = sin(a)
        val dist = 0.58f + (rng.nextFloat() - 0.5f) * 0.44f
        val edgeAlpha = (0.42f + rng.nextFloat() * 0.40f - sinA.coerceAtLeast(0f) * 0.22f)
            .coerceIn(0.18f, 0.92f)
        CP(a, dist.coerceIn(0.32f, 1.10f), edgeAlpha)
    }.withMicroNoise(rng, amplitude = 0.038f, freqScale = 3)
}

// ─────────────────────────────────────────────────────────────────────────────
// Tension constants per morphology
// ─────────────────────────────────────────────────────────────────────────────

private fun baseTension(morphology: Morphology): Float = when (morphology) {
    Morphology.WISPY     -> 0.26f
    Morphology.FRACTURED -> 0.28f
    Morphology.LAYERED   -> 0.30f
    Morphology.SMALL     -> 0.34f
    Morphology.TOWER     -> 0.38f
    Morphology.CUMULUS   -> 0.42f
}

// ─────────────────────────────────────────────────────────────────────────────
// Geometry caching builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bakes all [Path] objects for one sub-mass at unit scale.
 * Called once per sub-mass at cloud construction time.
 */
private fun bakeUnitMass(
    pts:         List<CP>,
    tension:     Float,
    offX:        Float,
    offY:        Float,
    scaleW:      Float,
    scaleH:      Float,
    alphaScale:  Float,
    brightBias:  Float,
    isShadow:    Boolean
): CachedMassPaths {
    val meanEdgeAlpha = pts.map { it.alpha }.average().toFloat().coerceIn(0.05f, 1f)

    return if (isShadow) {
        // Shadow masses: two draw passes at 1.08 and 1.00
        CachedMassPaths(
            expansionPaths = emptyArray(),
            crownPath      = null,
            shadowPaths    = arrayOf(
                unitSplinePath(pts, 1.08f, tension),
                unitSplinePath(pts, 1.00f, tension)
            ),
            edgeAlpha   = meanEdgeAlpha,
            offX        = offX,
            offY        = offY,
            scaleW      = scaleW,
            scaleH      = scaleH,
            alphaScale  = alphaScale,
            brightBias  = brightBias,
            isShadow    = true
        )
    } else {
        // Normal masses: four expansion passes + crown highlight
        CachedMassPaths(
            expansionPaths = arrayOf(
                unitSplinePath(pts, 1.38f, tension),    // wide outer vapour fringe
                unitSplinePath(pts, 1.20f, tension),    // soft atmospheric edge
                unitSplinePath(pts, 1.08f, tension),    // mid boundary
                unitSplinePath(pts, 1.00f, tension)     // core body
            ),
            crownPath   = unitSplinePath(pts, 0.80f, tension),
            shadowPaths = null,
            edgeAlpha   = meanEdgeAlpha,
            offX        = offX,
            offY        = offY,
            scaleW      = scaleW,
            scaleH      = scaleH,
            alphaScale  = alphaScale,
            brightBias  = brightBias,
            isShadow    = false
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-mass assembly
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Builds and pre-bakes all [CachedMassPaths] for one cloud.
 *
 * Structure per cloud:
 *   1. Primary outer envelope (full width, full height).
 *   2. 1–4 internal lobes offset into the cloud interior.
 *   3. Optional shadow pocket at underside (CUMULUS, TOWER, LAYERED).
 *   4. 0–2 edge-wisp tendrils dissolving at the lateral margins.
 */
private fun buildAndBakeMasses(
    rng:        Random,
    morphology: Morphology,
    depth:      Int
): List<CachedMassPaths> {
    val result = mutableListOf<CachedMassPaths>()

    // Point-count budget by depth plane
    val (outerN, lobeN, wispN) = when (depth) {
        0    -> Triple(10, 7, 5)
        1    -> Triple(14, 9, 6)
        else -> Triple(18, 11, 8)
    }

    val tension = baseTension(morphology)
    val lobeTension = tension + 0.05f  // lobes slightly looser so they merge

    fun outline(n: Int): List<CP> = when (morphology) {
        Morphology.CUMULUS   -> cumulusOutline(n, rng)
        Morphology.WISPY     -> wispyOutline(n, rng)
        Morphology.LAYERED   -> layeredOutline(n, rng)
        Morphology.FRACTURED -> fracturedOutline(n, rng)
        Morphology.TOWER     -> towerOutline(n, rng)
        Morphology.SMALL     -> smallOutline(n, rng)
    }

    // ── Primary envelope ─────────────────────────────────────────────────────
    result += bakeUnitMass(
        pts        = outline(outerN),
        tension    = tension,
        offX       = 0f, offY = 0f,
        scaleW     = 1.0f, scaleH = 1.0f,
        alphaScale = 1.0f,
        brightBias = 0f,
        isShadow   = false
    )

    // ── Internal lobes ────────────────────────────────────────────────────────
    // Lobe count is larger for near-plane clouds; tower and cumulus get 4 lobes
    val lobeCount = when {
        depth == 0                                               -> 1
        morphology == Morphology.CUMULUS && depth == 2          -> 4
        morphology == Morphology.TOWER   && depth == 2          -> 4
        depth == 2                                              -> 3
        else                                                    -> 2
    }
    repeat(lobeCount) { lobeIdx ->
        // Bias toward upper interior (negative Y = upward in screen space)
        val offX   = (rng.nextFloat() - 0.5f) * 0.36f
        val offY   = -(rng.nextFloat() * 0.28f)
        val sw     = 0.44f + rng.nextFloat() * 0.34f
        val sh     = 0.38f + rng.nextFloat() * 0.30f
        val bright = 0.10f - lobeIdx * 0.05f   // first lobe brightest
        result += bakeUnitMass(
            pts        = outline(lobeN),
            tension    = lobeTension,
            offX       = offX, offY = offY,
            scaleW     = sw, scaleH = sh,
            alphaScale = 0.38f + rng.nextFloat() * 0.32f,
            brightBias = bright,
            isShadow   = false
        )
    }

    // ── Shadow pocket ─────────────────────────────────────────────────────────
    if (morphology in listOf(Morphology.CUMULUS, Morphology.TOWER, Morphology.LAYERED)) {
        result += bakeUnitMass(
            pts        = layeredOutline(lobeN, rng),
            tension    = tension,
            offX       = (rng.nextFloat() - 0.5f) * 0.18f,
            offY       = 0.20f + rng.nextFloat() * 0.16f,
            scaleW     = 0.80f + rng.nextFloat() * 0.18f,
            scaleH     = 0.26f + rng.nextFloat() * 0.18f,
            alphaScale = -(0.12f + rng.nextFloat() * 0.18f),  // negative → shadow
            brightBias = 0f,
            isShadow   = true
        )
    }

    // ── Edge wisps ────────────────────────────────────────────────────────────
    val wispCount = when (depth) {
        0    -> 0
        1    -> if (rng.nextFloat() < 0.60f) 1 else 0
        else -> rng.nextInt(1, 3)   // 1 or 2
    }
    repeat(wispCount) {
        val side = if (rng.nextFloat() < 0.5f) -1f else 1f
        result += bakeUnitMass(
            pts        = wispyOutline(wispN, rng),
            tension    = 0.26f,
            offX       = side * (0.42f + rng.nextFloat() * 0.32f),
            offY       = (rng.nextFloat() - 0.60f) * 0.28f,
            scaleW     = 0.24f + rng.nextFloat() * 0.28f,
            scaleH     = 0.10f + rng.nextFloat() * 0.12f,
            alphaScale = 0.12f + rng.nextFloat() * 0.16f,
            brightBias = -0.06f,   // wisps slightly cooler/darker
            isShadow   = false
        )
    }

    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Morphology picker
// ─────────────────────────────────────────────────────────────────────────────

private fun pickMorphology(rng: Random, depth: Int): Morphology = when (depth) {
    0 -> when (rng.nextInt(5)) {
        0    -> Morphology.WISPY
        1, 2 -> Morphology.SMALL
        3    -> Morphology.LAYERED
        else -> Morphology.SMALL
    }
    1 -> when (rng.nextInt(6)) {
        0, 1 -> Morphology.CUMULUS
        2    -> Morphology.LAYERED
        3    -> Morphology.FRACTURED
        4    -> Morphology.WISPY
        else -> Morphology.CUMULUS
    }
    else -> when (rng.nextInt(7)) {
        0, 1, 2 -> Morphology.CUMULUS
        3       -> Morphology.TOWER
        4       -> Morphology.LAYERED
        5       -> Morphology.FRACTURED
        else    -> Morphology.CUMULUS
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cloud factory
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Constructs one fully-baked [OrganicCloud].
 *
 * The unique per-cloud seed is derived from [cloudIndex], [depth], the cloud
 * [style], and [System.nanoTime] — guaranteeing distinct geometry for every
 * cloud in every app session while remaining stable for the cloud's lifetime.
 */
private fun buildOrganicCloud(
    cloudIndex: Int,
    depth:      Int,
    style:      CloudStyle
): OrganicCloud {
    // Unique per-cloud, per-session seed
    val seed = System.nanoTime() xor
               (depth.toLong() * 13337L) xor
               (cloudIndex.toLong() * 7919L) xor
               (style.ordinal.toLong() * 3571L)
    val rng  = Random(seed)

    val morphology = pickMorphology(rng, depth)

    // ── Size ─────────────────────────────────────────────────────────────────
    val widthBase = when (depth) {
        0 -> when (morphology) {
            Morphology.WISPY   -> rng.nextFloat() * 0.14f + 0.10f
            Morphology.SMALL   -> rng.nextFloat() * 0.06f + 0.05f
            else               -> rng.nextFloat() * 0.09f + 0.09f
        }
        1 -> when (morphology) {
            Morphology.LAYERED -> rng.nextFloat() * 0.20f + 0.22f
            Morphology.WISPY   -> rng.nextFloat() * 0.16f + 0.15f
            else               -> rng.nextFloat() * 0.14f + 0.15f
        }
        else -> when (morphology) {
            Morphology.TOWER   -> rng.nextFloat() * 0.12f + 0.15f
            Morphology.LAYERED -> rng.nextFloat() * 0.26f + 0.28f
            else               -> rng.nextFloat() * 0.18f + 0.20f
        }
    }

    val aspectRatio = when (morphology) {
        Morphology.WISPY   -> 0.10f + rng.nextFloat() * 0.08f   // extremely flat
        Morphology.LAYERED -> 0.18f + rng.nextFloat() * 0.14f   // flat band
        Morphology.TOWER   -> 1.50f + rng.nextFloat() * 0.70f   // tall
        Morphology.SMALL   -> 0.55f + rng.nextFloat() * 0.35f   // roughly round
        else               -> 0.35f + rng.nextFloat() * 0.25f
    }

    // ── Base opacity ─────────────────────────────────────────────────────────
    val baseAlpha = when (depth) {
        0    -> rng.nextFloat() * 0.10f + 0.20f   // hazy distant
        1    -> rng.nextFloat() * 0.14f + 0.34f
        else -> rng.nextFloat() * 0.16f + 0.46f   // denser near
    }

    // ── Position ─────────────────────────────────────────────────────────────
    val x = rng.nextFloat()
    val y = when (depth) {
        0    -> rng.nextFloat() * 0.22f + 0.02f
        1    -> rng.nextFloat() * 0.25f + 0.04f
        else -> rng.nextFloat() * 0.22f + 0.08f
    }

    // ── Drift speed ──────────────────────────────────────────────────────────
    val speedBase = when (depth) {
        0    -> 0.000022f
        1    -> 0.000048f
        else -> 0.000088f
    }
    val morphSpeed = when (morphology) {
        Morphology.WISPY   -> 1.35f   // high-altitude jet stream feel
        Morphology.TOWER   -> 0.85f   // heavy storm cell moves slowly
        else               -> 1.0f
    }
    val speed = (speedBase + rng.nextFloat() * speedBase * 0.80f) * morphSpeed

    return OrganicCloud(
        x          = x,
        y          = y,
        width      = widthBase,
        height     = widthBase * aspectRatio,
        masses     = buildAndBakeMasses(rng, morphology, depth),
        speed      = speed,
        depth      = depth,
        baseAlpha  = baseAlpha,
        morphology = morphology
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Renderer — zero Path allocation per frame
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Draws one [OrganicCloud] using its pre-baked unit-scale [CachedMassPaths].
 *
 * Each mass is drawn by:
 *   1. Applying a [withTransform] that scales (halfW, halfH) and translates to
 *      the world-space centre of that mass — the Path objects never move.
 *   2. Drawing each cached expansion path at the appropriate gradient.
 *
 * Lighting model
 * ──────────────
 *   litColor    — sunlit upper surface (top of gradient)
 *   shadowColor — shadowed underside (bottom of gradient)
 *   midColor    — neutral interior body
 *   rimColor    — directional edge highlight (sunrise/sunset only)
 *
 * Atmospheric perspective
 * ───────────────────────
 *   [depthFade] 0 = near-plane (full colour), 1 = far-plane (desaturated).
 *   Both lit and shadow colours are interpolated toward a neutral atmospheric
 *   grey as distance increases, making far clouds hazy and low-contrast.
 *
 * Per-vertex alpha
 * ────────────────
 *   [CachedMassPaths.edgeAlpha] is the mean of all CP.alpha values in the mass.
 *   The outermost halo pass (1.38×) is drawn at [edgeAlpha] × base alpha so
 *   masses with dissolving edges actually appear translucent at their margins.
 */
private fun DrawScope.drawOrganicCloud(
    cloud:        OrganicCloud,
    litColor:     Color,
    shadowColor:  Color,
    midColor:     Color,
    rimColor:     Color,
    ambientAlpha: Float,
    isSunset:     Boolean,
    isSunrise:    Boolean,
    isNight:      Boolean,
    depthFade:    Float
) {
    val cxPx  = cloud.x * size.width
    val cyPx  = cloud.y * size.height
    val halfW = cloud.width  * size.minDimension * 0.5f
    val halfH = cloud.height * size.minDimension * 0.5f
    // Horizon integration fade:
    // Clouds whose normalised Y position is close to the horizon gradually lose
    // opacity so they dissolve naturally into the sky gradient rather than
    // terminating with a hard visible edge against the lower atmosphere.
    // Far-plane clouds (depthFade ≈ 1) start fading earlier because they are
    // conceptually more distant and more affected by atmospheric haze.
    //
    // Floor raised from 0.35 → 0.65 (fade max reduced from 0.65 → 0.35).
    // The old 0.35 floor collapsed cloud alpha to ~4% at night (sky is dark,
    // so clouds in the upper half of the canvas should stay visible, not dissolve).
    val horizonFadeStart = 0.28f - depthFade * 0.08f   // far: 0.20, near: 0.28
    val horizonFadeEnd   = 0.42f - depthFade * 0.06f   // far: 0.36, near: 0.42
    val horizonAttenuation = when {
        cloud.y < horizonFadeStart -> 1.0f
        cloud.y < horizonFadeEnd   ->
            1f - ((cloud.y - horizonFadeStart) / (horizonFadeEnd - horizonFadeStart))
                .coerceIn(0f, 1f) * 0.35f
        else                       -> 0.65f
    }

    val base  = cloud.baseAlpha * ambientAlpha * horizonAttenuation

    // Atmospheric perspective: far clouds blend toward a sky-grey that matches
    // the ambient brightness. During daytime the haze is a pale blue-grey;
    // at night it is a very dark blue-grey matching the night sky so that far
    // clouds fade toward the correct dark background rather than a bright wash
    // that would look out of place in a dark scene.
    val atmMid   = if (isNight) Color(0.08f, 0.10f, 0.14f)   // dark night sky-grey
                   else        Color(0.70f, 0.72f, 0.75f)    // pale daytime haze
    val atmBlend = depthFade * 0.48f
    fun atmMix(c: Color) = Color(
        red   = c.red   * (1f - atmBlend) + atmMid.red   * atmBlend,
        green = c.green * (1f - atmBlend) + atmMid.green * atmBlend,
        blue  = c.blue  * (1f - atmBlend) + atmMid.blue  * atmBlend
    )
    val aLit    = atmMix(litColor)
    val aShadow = atmMix(shadowColor)
    val aMid    = atmMix(midColor)
    val aRim    = atmMix(rimColor)

    for (mass in cloud.masses) {
        // World-space centre and half-extents for this mass
        val mCx = cxPx  + mass.offX * halfW * 2f
        val mCy = cyPx  + mass.offY * halfH * 2f
        val mHW = halfW * mass.scaleW
        val mHH = halfH * mass.scaleH

        // ── Shadow darkening pass ─────────────────────────────────────────
        if (mass.isShadow) {
            val paths = mass.shadowPaths ?: continue
            val mAlpha = abs(mass.alphaScale)
            // Gradient fades in from top, peaks at 50%, fades at base
            // Screen-space Y bounds for gradient anchoring
            val top = mCy - mHH * 1.4f
            val bot = mCy + mHH * 0.8f
            val shadowBrush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to aShadow.copy(alpha = 0f),
                    0.22f to aShadow.copy(alpha = base * mAlpha * 0.26f),
                    0.52f to aShadow.copy(alpha = base * mAlpha * 0.55f),
                    0.78f to aShadow.copy(alpha = base * mAlpha * 0.40f),
                    1.00f to aShadow.copy(alpha = 0f)
                ),
                startY = top, endY = bot
            )
            // Draw using withTransform: scale unit path to mass size, then translate
            withTransform({
                translate(mCx, mCy)
                scale(mHW, mHH)
            }) {
                drawPath(paths[0], shadowBrush)
                drawPath(paths[1], shadowBrush)
            }
            continue
        }

        // ── Effective lit/shadow colours ──────────────────────────────────
        val effLit: Color
        val effShadow: Color
        when {
            isNight -> {
                effLit    = aLit
                effShadow = aShadow
            }
            isSunset -> {
                val warmBlend = 0.42f
                effLit = Color(
                    red   = aLit.red   * (1f - warmBlend) + aRim.red   * warmBlend,
                    green = aLit.green * (1f - warmBlend) + aRim.green * warmBlend,
                    blue  = aLit.blue  * (1f - warmBlend) + aRim.blue  * warmBlend
                )
                effShadow = aShadow
            }
            isSunrise -> {
                val warmBlend = 0.28f   // softer sunrise tint than sunset
                effLit = Color(
                    red   = aLit.red   * (1f - warmBlend) + aRim.red   * warmBlend,
                    green = aLit.green * (1f - warmBlend) + aRim.green * warmBlend,
                    blue  = aLit.blue  * (1f - warmBlend) + aRim.blue  * warmBlend
                )
                effShadow = aShadow
            }
            else -> {
                effLit    = aLit
                effShadow = aShadow
            }
        }

        val brightAdj = (1f + mass.brightBias).coerceIn(0.68f, 1.28f)
        val mAlpha    = mass.alphaScale
        val litCap    = if (isNight) 0.58f else 0.88f

        // Screen-space Y bounds for gradient anchoring (same scale as withTransform)
        val massTop = mCy - mHH * 1.40f
        val massBot = mCy + mHH * 0.80f

        // ── Per-vertex edge alpha: mean edge dissolve for the outermost pass ─
        // This is the fix for the previously-unused CP.alpha data.
        val edgeDissolve = mass.edgeAlpha

        // Helper: build the vertical gradient for one alpha multiplier
        // The startY/endY are in absolute screen coordinates; withTransform
        // below maps the unit-scale path to those same coordinates correctly
        // because Brush.verticalGradient is in canvas space, not local space.
        fun massGrad(alphaMulti: Float): Brush {
            val a = base * mAlpha * alphaMulti * brightAdj
            return Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to effLit.copy(alpha    = (a * 0.50f).coerceAtMost(litCap * alphaMulti)),
                    0.10f to effLit.copy(alpha    = (a * 0.84f).coerceAtMost(litCap * alphaMulti)),
                    0.26f to effLit.copy(alpha    = (a * 0.94f).coerceAtMost(litCap * alphaMulti)),
                    0.46f to aMid.copy(alpha      = a * 0.86f),
                    0.64f to effShadow.copy(alpha = a * 0.70f),
                    0.82f to effShadow.copy(alpha = a * 0.38f),
                    1.00f to effShadow.copy(alpha = 0f)
                ),
                startY = massTop, endY = massBot
            )
        }

        // ── Draw expansion passes back-to-front ───────────────────────────
        withTransform({ translate(mCx, mCy); scale(mHW, mHH) }) {
            // Pass 0: wide vapour fringe — alpha modulated by per-vertex edge dissolve
            drawPath(mass.expansionPaths[0], massGrad(0.09f * edgeDissolve))
            // Pass 1: soft atmospheric edge
            drawPath(mass.expansionPaths[1], massGrad(0.20f))
            // Pass 2: mid boundary
            drawPath(mass.expansionPaths[2], massGrad(0.42f))
            // Pass 3: core body — full density
            drawPath(mass.expansionPaths[3], massGrad(0.80f))
        }

        // ── Crown highlight (not drawn at night) ──────────────────────────
        if (!isNight && mass.crownPath != null) {
            val rimA     = when {
                isSunset  -> base * mAlpha * 0.50f
                isSunrise -> base * mAlpha * 0.38f
                else      -> base * mAlpha * 0.26f
            }
            val crownEnd = massTop + (massBot - massTop) * 0.30f
            withTransform({ translate(mCx, mCy); scale(mHW, mHH * 0.72f) }) {
                drawPath(
                    mass.crownPath,
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to aRim.copy(alpha = rimA * 0.84f),
                            0.38f to aRim.copy(alpha = rimA * 0.38f),
                            0.68f to aRim.copy(alpha = rimA * 0.10f),
                            1.00f to aRim.copy(alpha = 0f)
                        ),
                        startY = massTop, endY = crownEnd
                    )
                )
            }
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

    // Cloud count per depth plane — unchanged from original
    val (farCount, midCount, nearCount) = when (style) {
        CloudStyle.CLEAR     -> Triple(0, 0, 0)
        CloudStyle.FEW       -> Triple(1, 1, 1)
        CloudStyle.SCATTERED -> Triple(2, 2, 1)
        CloudStyle.BROKEN    -> Triple(3, 2, 2)
        CloudStyle.OVERCAST  -> Triple(4, 3, 2)
        CloudStyle.STORM     -> Triple(5, 4, 3)
    }

    val isStorm    = style == CloudStyle.STORM
    val isOvercast = style == CloudStyle.OVERCAST

    // ── Continuous solar elevation — from the ONE authoritative theme value ───
    // Matches SkyLayer exactly because both read theme.solarElevNorm, which
    // AuraEngine computes once per theme refresh.
    val solarElev = theme.solarElevNorm

    // Derived booleans used by drawOrganicCloud geometry decisions
    val isSunrise = solarElev in -0.08f..0.12f && run {
        val now = TimeState.now()
        val h = now.hour + now.minute / 60f
        h < 14f   // ante-meridiem: treat as sunrise zone
    }
    val isSunset  = solarElev in -0.08f..0.12f && !isSunrise
    val isNight   = solarElev <= -0.10f

    // ── Cloud colour palette — continuous solar elevation model ───────────────
    //
    // Cloud lit/shadow/rim colours respond to the same solar arc that drives
    // SkyLayer.  The warmth is concentrated near the horizon-crossing zone
    // (solarElev ≈ 0) and fades symmetrically on both sides.
    //
    // Storm and overcast reduce contrast but do NOT replace natural sky tones.

    // Warm influence factor: peaks at solarElev=0 (horizon crossing), fades to 0
    // for solarElev ≤ -0.20 (night) and solarElev ≥ +0.30 (full day).
    val warmZone = when {
        solarElev < -0.20f -> 0f
        solarElev < 0f     -> (solarElev + 0.20f) / 0.20f   // -0.20..0 → 0..1
        solarElev < 0.30f  -> 1f - solarElev / 0.30f         // 0..0.30 → 1..0
        else               -> 0f
    }.coerceIn(0f, 1f)

    // Day influence: how "bright daylight" the cloud should look
    val dayFactor = solarElev.coerceIn(0f, 1f)

    // Anchor lit colours: night, warm-transition, full-day
    val litNight   = Color(0xFF606E80)   // moonlit silver-blue
    val litWarm    = Color(0xFFF2E4D0)   // warm sunrise/sunset ivory
    val litDay     = Color(0xFFF4F5F5)   // near-white neutral daylight

    val litColor: Color = when {
        isStorm    -> Color(0xFF68727A)
        isOvercast -> Color(0xFFC8CAC8)
        else -> {
            // Blend: night → warm → day continuously
            val base = if (solarElev <= 0f) {
                lerpColorAtm(litNight, litWarm, warmZone)
            } else {
                lerpColorAtm(litWarm, litDay, dayFactor / 1f)
            }
            base
        }
    }

    // Shadow: always cooler/darker than lit; more violet near twilight
    val shadowNight   = Color(0xFF1E2838)   // deep cool dark
    val shadowTwilight = Color(0xFF3A3858)  // violet-grey twilight underside
    val shadowDay     = Color(
        red   = (litColor.red   * 0.42f).coerceIn(0f, 1f),
        green = (litColor.green * 0.44f).coerceIn(0f, 1f),
        blue  = (litColor.blue  * 0.50f).coerceIn(0f, 1f)
    )
    val shadowColor: Color = when {
        isStorm    -> Color(0xFF262E38)
        isOvercast -> Color(0xFF848890)
        else -> when {
            solarElev <= -0.10f -> shadowNight
            solarElev <= 0.10f  -> lerpColorAtm(shadowNight, shadowTwilight, warmZone)
            else                -> lerpColorAtm(shadowTwilight, shadowDay, dayFactor)
        }
    }

    val midColor: Color = Color(
        red   = (litColor.red   * 0.70f + shadowColor.red   * 0.30f).coerceIn(0f, 1f),
        green = (litColor.green * 0.70f + shadowColor.green * 0.30f).coerceIn(0f, 1f),
        blue  = (litColor.blue  * 0.70f + shadowColor.blue  * 0.30f).coerceIn(0f, 1f)
    )

    // Rim colour: warm gold at horizon crossing; near-white by day; silver-blue at night
    val rimNight  = Color(0xFFA4B2C4)   // subtle moonlit silver-blue
    val rimWarm   = Color(0xFFE8B86A)   // warm peach-gold (sunrise/sunset rim)
    val rimDay    = Color(0xFFF4F2EE)   // near-white daytime

    val rimColor: Color = when {
        isStorm  -> Color(0xFF585E68)
        else -> when {
            solarElev <= -0.10f -> rimNight
            solarElev <= 0.10f  -> lerpColorAtm(rimNight, rimWarm, warmZone)
            solarElev <= 0.40f  -> lerpColorAtm(rimWarm, rimDay, (solarElev - 0.10f) / 0.30f)
            else                -> rimDay
        }
    }

    // Scene-level opacity multiplier — unchanged
    val sceneAlpha = when (style) {
        CloudStyle.STORM     -> 1.00f
        CloudStyle.OVERCAST  -> 0.94f
        CloudStyle.BROKEN    -> 0.84f
        CloudStyle.SCATTERED -> 0.78f
        CloudStyle.FEW       -> 0.72f
        CloudStyle.CLEAR     -> 0f
    }

    // ── Build clouds: unique seed per cloud, baked once per style ─────────────
    // remember(style) ensures clouds are rebuilt only when the weather changes.
    // Each buildOrganicCloud() call uses a unique time-based seed so no two
    // clouds share the same geometry even within one session.
    val clouds = remember(style) {
        var idx = 0
        mutableStateListOf<OrganicCloud>().also { list ->
            fun addPlane(count: Int, depth: Int) =
                repeat(count) { list += buildOrganicCloud(idx++, depth, style) }
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
                // Wispy and layered clouds respond differently to wind
                val morphWindScale = when (cloud.morphology) {
                    Morphology.WISPY   -> 1.40f   // faster in upper atmosphere
                    Morphology.LAYERED -> 0.80f   // broad, slow layer
                    Morphology.TOWER   -> 0.70f   // heavy, slow-moving
                    else               -> 1.00f
                }
                val depthWind = baseWind * (0.52f + cloud.depth * 0.25f) * morphWindScale
                cloud.x -= cloud.speed * depthWind
                if (cloud.x < -0.55f) cloud.x = 1.55f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw back-to-front: far (depth 0) first, near (depth 2) last.
        // depthFade: far = 1.0 (max haze), near = 0.0 (full colour)
        for (depthPass in 0..2) {
            val depthFade = (2 - depthPass) / 2f
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
                    isSunrise    = isSunrise,
                    isNight      = isNight,
                    depthFade    = depthFade
                )
            }
        }
    }
}
