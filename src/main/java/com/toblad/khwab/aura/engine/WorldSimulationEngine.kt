package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.AuraWorld

/**
 * Coordinates the simulation of the Aura world.
 *
 * The simulation is completely independent from
 * rendering and Compose.
 *
 * Update order:
 *
 * Time
 *   ?
 * Weather
 *   ?
 * Lighting
 *
 * A new immutable AuraWorld instance is returned
 * after every simulation step.
 */
class WorldSimulationEngine(

    private val timeEngine: TimeEngine = TimeEngine(),

    private val weatherEngine: WeatherEngine = WeatherEngine(),

    private val lightingEngine: LightingEngine = LightingEngine()

) {

    /**
     * Advances the simulation by one frame.
     */
    fun update(
        world: AuraWorld,
        clock: FrameClock
    ): AuraWorld {

        val newTime =
            timeEngine.update(
                world.time,
                clock
            )

        val newWeather =
            weatherEngine.refresh()

        val newLighting =
            lightingEngine.update(
                newTime,
                newWeather
            )

        return world.copy(

            time = newTime,

            weather = newWeather,

            lighting = newLighting
        )
    }
}
