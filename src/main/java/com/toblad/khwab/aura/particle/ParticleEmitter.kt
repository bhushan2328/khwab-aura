package com.toblad.khwab.aura.particle

/**
 * Creates particles for the particle system.
 *
 * Future implementations will generate particles
 * based on weather, animations and scene state.
 */
class ParticleEmitter {

    fun emit(
        system: ParticleSystem,
        particle: Particle
    ) {
        system.add(particle)
    }
}

