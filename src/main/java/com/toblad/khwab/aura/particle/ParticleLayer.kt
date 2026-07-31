package com.toblad.khwab.aura.particle

/**
 * Connects a particle system with its renderer.
 *
 * This class will later integrate with AuraRenderer
 * so particle effects become part of the scene.
 */
class ParticleLayer(

    val system: ParticleSystem = ParticleSystem(),
    val emitter: ParticleEmitter = ParticleEmitter(),
    val renderer: ParticleRenderer = ParticleRenderer()

) {

    fun render(): List<Particle> {

        return renderer.render(system)
    }
}

