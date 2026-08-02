package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.particle.ParticleSystem
import com.toblad.khwab.aura.renderer.RenderContext
import com.toblad.khwab.aura.scene.SceneGraph
import com.toblad.khwab.aura.world.AuraWorld
import com.toblad.khwab.aura.world.TimeState

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

    private val particleUpdater: ParticleUpdater = ParticleUpdater(),

    private val themeEngine: ThemeEngine = ThemeEngine(),

    private val timePhaseEngine: TimePhaseEngine = TimePhaseEngine(),

    private val weatherEngine: WeatherEngine = WeatherEngine()

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

    /**
     * Generates the current AuraTheme from the
     * simulated world (if running) and the supplied
     * configuration.
     */
    fun generateTheme(config: AuraConfig): AuraTheme {

        val auraState =
            if (config.enabled) AuraState.ACTIVE else AuraState.OFF

        val time: TimeState = world?.time ?: TimeState()

        val timePhase = timePhaseEngine.calculate(time)

        val worldWeather = world?.weather ?: weatherEngine.refresh()

        val weatherState =
            com.toblad.khwab.aura.model.WeatherState.valueOf(worldWeather.name)

        return themeEngine.createTheme(
            auraState = auraState,
            timePhase = timePhase,
            weatherState = weatherState,
            enabled = config.enabled
        )
    }

    /**
     * Refreshes and returns the current AuraTheme.
     *
     * Placeholder implementation until a full
     * refresh pipeline (re-simulating weather/time)
     * is added.
     */
    fun refresh(config: AuraConfig): AuraTheme {
        return generateTheme(config)
    }
}