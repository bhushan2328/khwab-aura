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
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.CloudStyle
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.world.TimeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Realistic drifting cloud layer.
 *
 * Each cloud is a cluster of soft feathered circles arranged on a procedural
 * cumulus profile — wide at the base, taller bumps on top — giving a natural
 * puffy look without any bitmaps.
 *
 * Three depth planes (far / mid / near) drift at different speeds producing a
 * subtle parallax effect. Each plane also has a different opacity and size range
 * so distant clouds appear hazy while nearby ones are bright and crisp.
 *
 * Each puff circle is drawn with a radial gradient that fades to transparent at
 * the rim so clouds have soft, feathered edges instead of hard circles.
 *
 * Volumetric lighting: the gradient is biased upward so the lit top face is
 * brighter than the shaded underside, simulating sunlight from above.
 *
 * Updated in-place — no per-tick allocation.
 */

// ── Data holder ────────────────────────────────────────────────────────────────

/**
 * One circle within a cloud cluster.
 * [rx] / [ry] are offsets from the cloud anchor in normalised-minDim units.
 * [r] is the radius in the same units.
 * [alpha] is the per-puff opacity multiplier (edge puffs are more transparent).
 */
private data class Puff(val rx: Float, val ry: Float, val r: Float, val alpha: Float)

/**
 * A single cloud composed of multiple overlapping [Puff]s.
 *
 * @param x       Horizontal position 0..1 (normalised to canvas width).
 * @param y       Vertical position 0..1 (normalised to canvas height).
 * @param puffs   Procedurally generated puff circles.
 * @param speed   Normalised drift per 16 ms tick (before wind multiplier).
 * @param depth   0 = far (slow, small, dim), 1 = mid, 2 = near (fast, large, bright).
 */
private class Cloud(
    var x: Float,
    val y: Float,
    val puffs: List<Puff>,
    val speed: Float,
    val depth: Int          // 0..2
)

// ── Procedural cloud builder ────────────────────────────────────────────────────

/**
 * Builds a realistic cumulus cloud profile at the given depth level.
 *
 * Strategy:
 *  1. Place a row of base puffs along the bottom (wide, overlapping).
 *  2. Add a row of medium puffs slightly above, inset horizontally.
 *  3. Add 1-2 taller bumps at the top centre.
 *  4. Each puff's alpha falls off with distance from the cloud centre.
 */
private fun buildCloud(rng: Random, depth: Int): List<Puff> {
    // Scale: far clouds are small, near clouds are large
    val baseR = when (depth) {
        0 -> rng.nextFloat() * 0.012f + 0.016f   // far:  0.016..0.028
        1 -> rng.nextFloat() * 0.016f + 0.024f   // mid:  0.024..0.040
        else -> rng.nextFloat() * 0.020f + 0.034f // near: 0.034..0.054
    }

    val puffs = mutableListOf<Puff>()

    // ── Bottom row: 4-7 base puffs spread horizontally ──────────────────────
    val baseCount = rng.nextInt(4, 8)
    val totalWidth = baseR * 1.6f * (baseCount - 1)

    for (i in 0 until baseCount) {
        val rx = -totalWidth / 2f + i * baseR * 1.6f + (rng.nextFloat() - 0.5f) * baseR * 0.4f
        val ry = (rng.nextFloat() - 0.5f) * baseR * 0.3f  // slight vertical jitter
        val r  = baseR * (0.85f + rng.nextFloat() * 0.30f)
        // Edge puffs are softer
        val edgeFactor = 1f - (2f * i.toFloat() / (baseCount - 1).coerceAtLeast(1) - 1f)
            .let { it * it } * 0.35f
        puffs += Puff(rx, ry, r, edgeFactor)
    }

    // ── Middle row: 3-5 puffs, inset, slightly higher ───────────────────────
    val midCount = rng.nextInt(3, 6)
    val midWidth = totalWidth * 0.65f
    for (i in 0 until midCount) {
        val rx = -midWidth / 2f + i * (midWidth / (midCount - 1).coerceAtLeast(1)) +
                 (rng.nextFloat() - 0.5f) * baseR * 0.3f
        val ry = -(baseR * 0.9f) + (rng.nextFloat() - 0.5f) * baseR * 0.25f
        val r  = baseR * (0.75f + rng.nextFloat() * 0.35f)
        val edgeFactor = 1f - (2f * i.toFloat() / (midCount - 1).coerceAtLeast(1) - 1f)
            .let { it * it } * 0.4f
        puffs += Puff(rx, ry, r, edgeFactor * 0.90f)
    }

    // ── Top bumps: 1-3 taller puffs at the crown ────────────────────────────
    val topCount = rng.nextInt(1, 4)
    for (i in 0 until topCount) {
        val spread = totalWidth * 0.3f
        val rx = (rng.nextFloat() - 0.5f) * spread
        val ry = -(baseR * 1.7f) - rng.nextFloat() * baseR * 0.4f
        val r  = baseR * (0.55f + rng.nextFloat() * 0.30f)
        puffs += Puff(rx, ry, r, 0.80f - i * 0.12f)
    }

    return puffs
}

// ── Composable ─────────────────────────────────────────────────────────────────

@Composable
fun CloudLayer(theme: AuraTheme) {

    val style = theme.profile.clouds
    if (style == CloudStyle.CLEAR) return

    val isResumed by rememberIsResumed()

    // Total cloud count per depth plane — heavier styles add more per plane
    val (farCount, midCount, nearCount) = when (style) {
        CloudStyle.CLEAR     -> Triple(0, 0, 0)
        CloudStyle.FEW       -> Triple(1, 1, 1)
        CloudStyle.SCATTERED -> Triple(2, 2, 1)
        CloudStyle.BROKEN    -> Triple(3, 2, 2)
        CloudStyle.OVERCAST  -> Triple(4, 3, 2)
        CloudStyle.STORM     -> Triple(5, 4, 3)
    }

    // ── Cloud face colour by time of day ──────────────────────────────────────
    val currentHour = remember { TimeState.now().hour + TimeState.now().minute / 60f }
    val faceColor: Color = when {
        style == CloudStyle.STORM    -> Color(0xCC8A8A8A)   // dark threatening grey
        style == CloudStyle.OVERCAST -> Color(0xCCCCCCCC)   // flat grey ceiling
        else -> when (theme.timePhase) {
            TimePhase.PRE_DAWN  -> Color(0xCC9FB3C8)        // cool blue-grey
            TimePhase.SUNRISE   -> Color(0xEEFFD0BB)        // warm peachy-pink
            TimePhase.MORNING   -> {
                // Blend from warm peach (06:00) to bright white (10:00)
                val t = ((currentHour - 6f) / 4f).coerceIn(0f, 1f)
                Color(
                    red   = (0xFF / 255f) * 1f,
                    green = (0xD0 / 255f) + ((1f - 0xD0 / 255f)) * t,
                    blue  = (0xBB / 255f) + ((1f - 0xBB / 255f)) * t,
                    alpha = 0xEE / 255f
                )
            }
            TimePhase.NOON      -> Color(0xF0FFFFFF)        // crisp white
            TimePhase.AFTERNOON -> Color(0xEEFFF4D6)        // warm golden-white
            TimePhase.SUNSET    -> Color(0xEEFF9A6A)        // vivid warm orange
            TimePhase.EVENING   -> Color(0xDDD4DAF0)        // cool lavender-grey
            TimePhase.NIGHT,
            TimePhase.MIDNIGHT  -> Color(0xBBA8B8C8)        // dim grey-blue
        }
    }

    // Underside shadow — darker and desaturated relative to face
    val shadowColor: Color = when {
        style == CloudStyle.STORM    -> Color(0xAA555555)
        style == CloudStyle.OVERCAST -> Color(0xBB999999)
        else -> Color(
            red   = faceColor.red   * 0.60f,
            green = faceColor.green * 0.62f,
            blue  = faceColor.blue  * 0.68f,
            alpha = faceColor.alpha * 0.70f
        )
    }

    // Per-depth opacity multipliers: far clouds are hazy, near are crisp
    val depthAlpha = floatArrayOf(0.45f, 0.70f, 0.92f)

    // ── Build clouds once per style ────────────────────────────────────────────
    val rng = remember(style) { Random(style.ordinal * 1337L) }
    val clouds = remember(style) {
        mutableStateListOf<Cloud>().also { list ->
            fun addPlane(count: Int, depth: Int) {
                // Speed range: far = 0.5×, near = 1.5× of base
                val speedBase = when (depth) {
                    0    -> 0.000035f
                    1    -> 0.000065f
                    else -> 0.000110f
                }
                repeat(count) {
                    list += Cloud(
                        x      = rng.nextFloat(),
                        // Spread vertically: far clouds are higher, near clouds lower
                        y      = when (depth) {
                            0    -> rng.nextFloat() * 0.18f + 0.02f
                            1    -> rng.nextFloat() * 0.20f + 0.06f
                            else -> rng.nextFloat() * 0.18f + 0.10f
                        },
                        puffs  = buildCloud(rng, depth),
                        speed  = speedBase + rng.nextFloat() * speedBase * 0.6f,
                        depth  = depth
                    )
                }
            }
            addPlane(farCount,  depth = 0)
            addPlane(midCount,  depth = 1)
            addPlane(nearCount, depth = 2)
        }
    }

    // Wind from AnimationLayer scales drift speed
    val windIntensity = LocalWindIntensity.current

    LaunchedEffect(style, isResumed, theme.animationsEnabled) {
        if (!isResumed || !theme.animationsEnabled) return@LaunchedEffect
        while (isActive) {
            // Deeper (nearer) clouds feel more wind
            val baseWind = 1f + windIntensity * 1.8f
            for (cloud in clouds) {
                val depthWind = baseWind * (0.6f + cloud.depth * 0.2f)
                cloud.x -= cloud.speed * depthWind
                if (cloud.x < -0.35f) cloud.x = 1.35f
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val minDim = size.minDimension

        // Draw back-to-front so far plane is behind near plane
        for (depthPass in 0..2) {
            for (cloud in clouds) {
                if (cloud.depth != depthPass) continue

                val cx = cloud.x * size.width
                val cy = cloud.y * size.height
                val planeAlpha = depthAlpha[cloud.depth]

                for (puff in cloud.puffs) {
                    val px = cx + puff.rx * minDim
                    val py = cy + puff.ry * minDim
                    val r  = puff.r * minDim
                    val a  = planeAlpha * puff.alpha

                    // Gradient center biased upward — lit top, shadowed base
                    val litCenter = Offset(px, py - r * 0.30f)

                    // Three-stop radial gradient:
                    //   centre (lit top) → face colour → shadow underside → transparent rim
                    // The transparent stop at radius 1.0 gives the soft feathered edge.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.00f to faceColor.copy(alpha = a),
                                0.55f to faceColor.copy(alpha = a * 0.90f),
                                0.78f to shadowColor.copy(alpha = a * 0.70f),
                                1.00f to shadowColor.copy(alpha = 0f)
                            ),
                            center = litCenter,
                            radius = r * 1.15f   // gradient slightly wider than circle → smooth falloff
                        ),
                        radius = r,
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
