package com.toblad.khwab.aura.particle

/**
 * Responsible for rendering particles.
 *
 * Rendering implementation will be added
 * when the Compose drawing layer is introduced.
 */
class ParticleRenderer {

    fun render(
        system: ParticleSystem
    ): List<Particle> {

        return system.particles
    }
}

