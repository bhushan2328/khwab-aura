package com.toblad.khwab.aura.ui

import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.world.TimeState
import kotlin.math.PI
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Shared atmospheric helpers
//
// Shared by SkyLayer, CloudLayer, WeatherLayer, BirdLayer.
// Kept minimal — only helpers that multiple layers need.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns a normalised solar elevation in [-1, +1].
 *
 *   +1.0  solar noon
 *    0.0  exact horizon crossing (sunrise / sunset)
 *   -1.0  solar midnight (deepest night)
 *
 * Uses the real sunrise/sunset hours from AuraTheme when available.
 * Falls back to a fixed-clock approximation when GPS data is absent.
 */
/**
 * Public overload so AuraEngine (outside the ui package) can compute
 * the canonical solar elevation norm without duplicating the formula.
 * The internal overload (below) remains for legacy internal call sites.
 */
fun solarElevationNormPublic(
    currentHour: Float,
    sunriseHour: Float?,
    sunsetHour:  Float?
): Float = solarElevationNorm(currentHour, sunriseHour, sunsetHour)

internal fun solarElevationNorm(
    currentHour: Float,
    sunriseHour: Float?,
    sunsetHour:  Float?
): Float {

    if (sunriseHour != null && sunsetHour != null && sunsetHour > sunriseHour) {
        val dayLength   = sunsetHour - sunriseHour
        val nightLength = 24f - dayLength
        val tDay        = (currentHour - sunriseHour) / dayLength

        if (tDay in 0f..1f) return sin(PI.toFloat() * tDay)

        val tNight = if (currentHour >= sunsetHour)
            (currentHour - sunsetHour) / nightLength
        else
            (currentHour + (24f - sunsetHour)) / nightLength
        return -(sin(PI.toFloat() * tNight))
    }

    // Fallback: 06:00 sunrise, 20:00 sunset
    val rise = 6f
    val set  = 20f
    val day  = set - rise
    return when {
        currentHour in rise..set -> {
            val tDay = (currentHour - rise) / day
            sin(PI.toFloat() * tDay)
        }
        currentHour > set -> {
            val nightLen = 24f - day
            val tNight = (currentHour - set) / nightLen
            -(sin(PI.toFloat() * tNight))
        }
        else -> {
            val nightLen = 24f - day
            val tNight = (currentHour + (24f - set)) / nightLen
            -(sin(PI.toFloat() * tNight))
        }
    }
}

/**
 * Convenience: computes the current solar elevation norm from TimeState.now()
 * and the given sunrise/sunset hours.
 */
internal fun currentSolarElevNorm(sunriseHour: Float?, sunsetHour: Float?): Float {
    val now = TimeState.now()
    val h   = now.hour + now.minute / 60f + now.second / 3600f
    return solarElevationNorm(h, sunriseHour, sunsetHour)
}

/** Linear interpolation clamped to [0,1]. */
internal fun lerpAtm(a: Float, b: Float, t: Float): Float =
    a + (b - a) * t.coerceIn(0f, 1f)

/** Per-channel RGB linear interpolation.  Alpha is 1f. */
internal fun lerpColorAtm(a: Color, b: Color, t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    return Color(
        red   = lerpAtm(a.red,   b.red,   tc),
        green = lerpAtm(a.green, b.green, tc),
        blue  = lerpAtm(a.blue,  b.blue,  tc),
        alpha = 1f
    )
}
