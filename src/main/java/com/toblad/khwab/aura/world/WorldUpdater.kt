package com.toblad.khwab.aura.world

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Base interface for every world simulation system.
 *
 * Each implementation receives the current world
 * and returns an updated immutable world.
 * ------------------------------------------------------------------
 */
interface WorldUpdater {

    /**
     * Updates the world state.
     *
     * @param world Current immutable world.
     * @param deltaSeconds Time elapsed since last update.
     *
     * @return Updated immutable world.
     */
    fun update(
        world: AuraWorld,
        deltaSeconds: Float
    ): AuraWorld
}
