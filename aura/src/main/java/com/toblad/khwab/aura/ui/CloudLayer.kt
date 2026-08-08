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
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.CloudStyle
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

    // Storm clouds are darker, overcast are grey-white
    val cloudColor = when (style) {
        CloudStyle.STORM    -> Color(0xCC9E9E9E)
        CloudStyle.OVERCAST -> Color(0xDDEEEEEE)
        else                -> Color(0xEEFFFFFF)
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

    LaunchedEffect(style, isResumed) {
        if (!isResumed) return@LaunchedEffect
        while (isActive) {
            for (cloud in clouds) {
                cloud.x -= cloud.speed        // drift left
                if (cloud.x < -0.25f) cloud.x = 1.25f  // wrap around
            }
            delay(16L)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val minDim = size.minDimension
        for (cloud in clouds) {
            val cx = cloud.x * size.width
            val cy = cloud.y * size.height
            cloud.radii.forEachIndexed { i, radiusFraction ->
                val r = radiusFraction * minDim
                drawCircle(
                    color  = cloudColor,
                    radius = r,
                    center = Offset(cx + cloud.offsets[i].x * minDim, cy + cloud.offsets[i].y * minDim)
                )
            }
        }
    }
}
