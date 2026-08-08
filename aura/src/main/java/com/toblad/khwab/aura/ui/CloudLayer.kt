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
import kotlin.random.Random

/**
 * Drifting cloud puffs.
 *
 * Each cloud is a cluster of overlapping circles that slowly
 * drift from right to left. Count and tint depend on the
 * CloudStyle from the current ThemeProfile.
 *
 * Mutable class updated in-place — no per-tick allocation.
 */

private class CloudPuff(
    var x: Float,          // 0..1 normalised
    val y: Float,          // 0..1 normalised
    val radii: List<Float>,// cluster circle radii (px-independent multipliers)
    val offsets: List<Offset>,// relative offsets for each circle in the cluster
    val speed: Float       // normalised units per 16 ms tick
)

@Composable
fun CloudLayer(theme: AuraTheme) {

    val style = theme.profile.clouds
    if (style == CloudStyle.CLEAR) return

    val isResumed by rememberIsResumed()

    // Number + darkness of clouds depends on style
    val count = when (style) {
        CloudStyle.CLEAR     -> 0
        CloudStyle.FEW       -> 2
        CloudStyle.SCATTERED -> 4
        CloudStyle.BROKEN    -> 6
        CloudStyle.OVERCAST  -> 9
        CloudStyle.STORM     -> 13
    }

    // Cloud colour: storm/overcast have fixed dark/grey tints.
    // For lighter styles the tint shifts with the time of day.
    // MORNING is split by actual clock hour:
    //   early morning (sunrise + ≤08:00) → warm pink-orange
    //   late morning  (08:00–12:00)      → transitions to white
    val currentHour = remember { TimeState.now().hour + TimeState.now().minute / 60f }
    val cloudColor = when {
        style == CloudStyle.STORM    -> Color(0xCC9E9E9E)
        style == CloudStyle.OVERCAST -> Color(0xDDEEEEEE)
        else -> when (theme.timePhase) {
            TimePhase.PRE_DAWN  -> Color(0xDDB0C4DE)  // cool blue-grey
            TimePhase.SUNRISE   -> Color(0xEEFFCCBB)  // warm pink-orange
            TimePhase.MORNING   -> {
                // Interpolate from warm pink (06:00) to pure white (10:00+)
                val t = ((currentHour - 6f) / 4f).coerceIn(0f, 1f)
                val warmR = 0xFF / 255f; val warmG = 0xCC / 255f; val warmB = 0xBB / 255f
                Color(
                    red   = warmR + (1f - warmR) * t,
                    green = warmG + (1f - warmG) * t,
                    blue  = warmB + (1f - warmB) * t,
                    alpha = 0xEE / 255f
                )
            }
            TimePhase.NOON      -> Color(0xEEFFFFFF)  // pure white
            TimePhase.AFTERNOON -> Color(0xEEFFF8E1)  // slightly golden
            TimePhase.SUNSET    -> Color(0xEEFFAA80)  // deep warm orange
            TimePhase.EVENING   -> Color(0xDDE8EAF6)  // cool violet-grey
            TimePhase.NIGHT,
            TimePhase.MIDNIGHT  -> Color(0xBBB0BEC5)  // dim grey-blue
        }
    }

    val clouds = remember(style) {
        mutableStateListOf<CloudPuff>().apply {
            repeat(count) {
                // Each cloud is a cluster of 3–5 overlapping circles
                val clusterSize = Random.nextInt(3, 6)
                val baseRadius  = Random.nextFloat() * 0.025f + 0.025f  // fraction of minDimension

                val radii = List(clusterSize) { baseRadius * (0.7f + Random.nextFloat() * 0.6f) }
                val offsets = List(clusterSize) {
                    Offset(
                        (Random.nextFloat() - 0.5f) * baseRadius * 2.5f,
                        (Random.nextFloat() - 0.5f) * baseRadius * 0.8f
                    )
                }

                add(CloudPuff(
                    x       = Random.nextFloat(),
                    y       = Random.nextFloat() * 0.35f,
                    radii   = radii,
                    offsets = offsets,
                    speed   = 0.00008f + Random.nextFloat() * 0.00006f
                ))
            }
        }
    }

    // Read wind from AnimationLayer's CompositionLocal — scales cloud speed
    val windIntensity = LocalWindIntensity.current

    LaunchedEffect(style, isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            // Base speed + wind bonus (storm = 3× base drift)
            val speedMult = 1f + windIntensity * 2.0f
            for (cloud in clouds) {
                cloud.x -= cloud.speed * speedMult
                if (cloud.x < -0.25f) cloud.x = 1.25f  // wrap around
            }
            delay(16L)
        }
    }

    // Underside shadow tint — slightly darker/greyer than the cloud face
    val shadowColor = when {
        style == CloudStyle.STORM    -> Color(0xAA757575)
        style == CloudStyle.OVERCAST -> Color(0xBBBBBBBB)
        else -> cloudColor.copy(alpha = cloudColor.alpha * 0.55f)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val minDim = size.minDimension
        for (cloud in clouds) {
            val cx = cloud.x * size.width
            val cy = cloud.y * size.height
            cloud.radii.forEachIndexed { i, radiusFraction ->
                val r  = radiusFraction * minDim
                val px = cx + cloud.offsets[i].x * minDim
                val py = cy + cloud.offsets[i].y * minDim

                // Radial gradient: bright top face → shadow underside
                // Gives each puff a soft 3-D volume look instead of flat solid fill
                val gradientCenter = Offset(px, py - r * 0.25f)  // offset slightly upward
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            cloudColor,
                            cloudColor.copy(alpha = cloudColor.alpha * 0.85f),
                            shadowColor
                        ),
                        center = gradientCenter,
                        radius = r * 1.05f
                    ),
                    radius = r,
                    center = Offset(px, py)
                )
            }
        }
    }
}
