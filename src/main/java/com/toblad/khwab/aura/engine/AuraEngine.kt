package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.particle.ParticleSystem
import com.toblad.khwab.aura.renderer.RenderContext
import com.toblad.khwab.aura.scene.SceneGraph
import com.toblad.khwab.aura.world.AuraWorld

/**
 * Central runtime engine coordinating Aura.
 *
 * AuraEngine owns the current AuraWorld and
 * delegates simulation and rendering updates
 * to specialized engines.
 */
class AuraEngine(

    private val worldSimulationEngine: WorldSimulationEngine = WorldSimulationEngine(),

    private val sceneUpdater: SceneUpdater = SceneUpdater(),

    private val animationUpdater: AnimationUpdater = AnimationUpdater(),

    private val particleUpdater: ParticleUpdater = ParticleUpdater()

) {

    var state: EngineState = EngineState.STOPPED
        private set

    /**
     * Current simulated world.
     */
    private var world: AuraWorld? = null

    fun start(initialWorld: AuraWorld) {

        world = initialWorld

        state = EngineState.RUNNING
    }

    fun pause() {

        state = EngineState.PAUSED
    }

    fun stop() {

        state = EngineState.STOPPED

        world = null
    }

    /**
     * Returns the current simulated world.
     */
    fun getWorld(): AuraWorld? = world

    /**
     * Advances Aura by one frame.
     */
    fun update(
        context: RenderContext,
        clock: FrameClock
    ) {

        val currentWorld = world ?: return

        val updatedWorld =
            worldSimulationEngine.update(
                currentWorld,
                clock
            )

        world = updatedWorld

        sceneUpdater.update(
            updatedWorld.scene,
            clock
        )

        animationUpdater.update(
            context,
            clock
        )

        particleUpdater.update(
            updatedWorld.particles,
            clock
        )
    }
}
