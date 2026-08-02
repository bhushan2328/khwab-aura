package com.toblad.khwab.aura.world

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Updates the simulated Aura world.
 *
 * This class owns the simulation step and
 * coordinates updates to the various world
 * subsystems.
 *
 * Rendering is NOT performed here.
 * ------------------------------------------------------------------
 */
class WorldSimulation(

    private var world: AuraWorld

) {

    /**
     * Returns the current immutable world.
     */
    fun world(): AuraWorld = world

    /**
     * Advances the simulation.
     *
     * Future versions will update:
     * - Time
     * - Weather
     * - Lighting
     * - Environment
     * - Particles
     */
    fun update(
        deltaSeconds: Float
    ) {

        // Placeholder for future simulation.

        world = world.copy()

    }
}
