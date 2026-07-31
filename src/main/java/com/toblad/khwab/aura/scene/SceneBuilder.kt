package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.world.AuraWorld

/**
 * Builds a renderable SceneGraph from the
 * current AuraWorld.
 *
 * SceneBuilder is responsible only for
 * constructing the scene hierarchy.
 *
 * It does not perform rendering,
 * animation or simulation.
 */
class SceneBuilder {

    /**
     * Builds a complete scene graph.
     */
    fun build(
        world: AuraWorld
    ): SceneGraph {

        val scene = SceneGraph()

        buildSky(scene, world)

        buildSunAndMoon(scene, world)

        buildClouds(scene, world)

        buildWeather(scene, world)

        buildLighting(scene, world)

        buildParticles(scene, world)

        return scene
    }

    private fun buildSky(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }

    private fun buildSunAndMoon(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }

    private fun buildClouds(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }

    private fun buildWeather(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }

    private fun buildLighting(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }

    private fun buildParticles(
        scene: SceneGraph,
        world: AuraWorld
    ) {
        // TODO
    }
}
