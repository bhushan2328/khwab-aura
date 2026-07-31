package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.LightingState
import com.toblad.khwab.aura.world.TimeState
import com.toblad.khwab.aura.world.WeatherState

/**
 * Computes the world's lighting based on
 * simulated time and weather.
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

        val baseIntensity = when (time.hour) {

            in 0..4 -> 0.25f

            in 5..6 -> 0.45f

            in 7..10 -> 0.75f

            in 11..14 -> 1.00f

            in 15..17 -> 0.90f

            in 18..19 -> 0.55f

            else -> 0.25f
        }

        val weatherMultiplier = when (weather) {

            WeatherState.CLEAR -> 1.00f

            WeatherState.CLOUDY -> 0.80f

            WeatherState.RAIN -> 0.65f

            WeatherState.FOG -> 0.60f

            WeatherState.SNOW -> 0.90f

            WeatherState.STORM -> 0.45f
        }

        val intensity =
            (baseIntensity * weatherMultiplier)
                .coerceIn(0f, 1f)

        return LightingState(
            intensity = intensity,
            ambient = intensity
        )
    }
}
