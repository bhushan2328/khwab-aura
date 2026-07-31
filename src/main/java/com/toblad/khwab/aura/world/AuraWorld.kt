package com.toblad.khwab.aura.world

import com.toblad.khwab.aura.particle.ParticleSystem
import com.toblad.khwab.aura.scene.SceneGraph

/**
 * Central world state for the Aura engine.
 */
data class AuraWorld(

    val scene: SceneGraph,

    val particles: ParticleSystem = ParticleSystem(),

    val environment: Environment = Environment(),

    val time: TimeState = TimeState(),

    val weather: WeatherState = WeatherState.CLEAR,

    val lighting: LightingState = LightingState()
)

