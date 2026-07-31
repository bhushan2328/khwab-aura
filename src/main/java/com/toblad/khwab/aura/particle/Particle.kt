package com.toblad.khwab.aura.particle

/**
 * Represents a single visual particle in Aura.
 *
 * A particle may represent:
 * - Rain
 * - Snow
 * - Leaf
 * - Petal
 * - Firefly
 * - Star
 * - Dust
 * - Spark
 *
 * Future versions will animate these values
 * every frame.
 */
data class Particle(

    /**
     * Unique particle identifier.
     */
    val id: Long,

    /**
     * Type of particle.
     */
    val type: ParticleType,

    /**
     * Horizontal position (0.0 - 1.0).
     */
    val x: Float,

    /**
     * Vertical position (0.0 - 1.0).
     */
    val y: Float,

    /**
     * Horizontal velocity.
     */
    val velocityX: Float = 0f,

    /**
     * Vertical velocity.
     */
    val velocityY: Float = 0f,

    /**
     * Particle size multiplier.
     */
    val size: Float = 1f,

    /**
     * Opacity (0.0 - 1.0).
     */
    val alpha: Float = 1f,

    /**
     * Remaining lifetime in seconds.
     */
    val life: Float = 1f
)

