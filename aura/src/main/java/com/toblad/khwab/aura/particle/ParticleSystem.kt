package com.toblad.khwab.aura.particle

/**
 * Collection of active particles.
 */
data class ParticleSystem(

    val particles: MutableList<Particle> = mutableListOf()

) {

    fun add(
        particle: Particle
    ) {
        particles += particle
    }

    fun remove(
        particle: Particle
    ) {
        particles -= particle
    }

    fun clear() {
        particles.clear()
    }
}

