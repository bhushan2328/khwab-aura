package com.toblad.khwab.aura.particle

/**
 * Defines all supported particle types in Aura.
 *
 * New particle types can be added without
 * changing the particle engine architecture.
 */
enum class ParticleType {

    /** Rain droplets */
    RAIN,

    /** Snow flakes */
    SNOW,

    /** Fog particles */
    FOG,

    /** Dust particles */
    DUST,

    /** Falling leaves */
    LEAF,

    /** Flower petals */
    PETAL,

    /** Fireflies */
    FIREFLY,

    /** Stars */
    STAR,

    /** Meteor trail */
    METEOR,

    /** Spark effects */
    SPARK,

    /** Generic glowing particle */
    GLOW
}

