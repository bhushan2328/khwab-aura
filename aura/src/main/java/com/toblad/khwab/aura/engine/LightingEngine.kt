package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.LightingState
import com.toblad.khwab.aura.world.TimeState
import com.toblad.khwab.aura.world.WeatherState
import kotlin.math.PI
import kotlin.math.sin

/**
 * Computes the world's lighting based on
 * simulated time and weather.
 *
 * Intensity is now derived from a continuous sine curve over
 * the 24-hour day instead of integer hour buckets, so there
 * are no visible snaps at hour boundaries.  The curve peaks
 * at solar noon (~13:00 local) and reaches its trough at
 * midnight (~01:00).
 *
 * This engine is responsible only for lighting
 * simulation and contains no rendering logic.
 */
class LightingEngine {

    /**
     * Calculates the current lighting state.
     */
    fun update(
        time: TimeState,
        weather: WeatherState
    ): LightingState {

        // Fractional hour in 0..24
        val h = time.hour + time.minute / 60f

        // Shift the sine curve so it peaks at 13:00 (solar noon offset by 1 h).
        // sin(-π/2) = -1 at trough, sin(π/2) = +1 at peak.
        // Phase offset: peak at h=13 → offset = (13/24)*2π - π/2
        val phaseOffset = (13f / 24f) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val raw = sin((h / 24f) * 2f * PI.toFloat() - phaseOffset)

        // Map sin -1..1  →  0.15..1.0  (sky is never completely black from the sun)
        val baseIntensity = ((raw + 1f) / 2f) * 0.85f + 0.15f

        // Hard-floor night hours (21:00–05:00) to a dim level so late-night
        // doesn't creep unrealistically high on the sine curve shoulder.
        val clampedBase = if (h < 5f || h >= 21f) baseIntensity.coerceAtMost(0.30f)
                          else baseIntensity

        val weatherMultiplier = when (weather) {

            WeatherState.CLEAR -> 1.00f

            WeatherState.CLOUDY -> 0.80f

            WeatherState.RAIN -> 0.65f

            WeatherState.FOG -> 0.60f

            WeatherState.SNOW -> 0.90f

            WeatherState.STORM -> 0.45f
        }

        val intensity =
            (clampedBase * weatherMultiplier)
                .coerceIn(0f, 1f)

        return LightingState(
            intensity = intensity,
            ambient = intensity
        )
    }
}
