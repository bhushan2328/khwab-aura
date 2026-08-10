package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.renderer.RenderContext
import com.toblad.khwab.aura.season.SeasonEngine
import com.toblad.khwab.aura.sun.MoonPhaseCalculator
import com.toblad.khwab.aura.sun.SolarCalculator
import com.toblad.khwab.aura.ui.solarElevationNormPublic
import com.toblad.khwab.aura.world.AuraWorld
import com.toblad.khwab.aura.world.TimeState
import kotlin.math.PI
import kotlin.math.cos

/**
 * Central runtime engine coordinating Aura.
 *
 * AuraEngine owns the current AuraWorld and
 * delegates simulation and rendering updates
 * to specialized engines.
 *
 * There is exactly ONE [WeatherEngine] instance shared between this class
 * and the [WorldSimulationEngine] it creates — both always see the same
 * weather state.
 */
class AuraEngine(

    private val themeEngine: ThemeEngine = ThemeEngine(),

    private val timePhaseEngine: TimePhaseEngine = TimePhaseEngine(),

    private val lightingEngine: LightingEngine = LightingEngine()

) {

    /**
     * Single authoritative weather engine — shared with WorldSimulationEngine
     * so updateWeather() and the simulation step always see the same state.
     */
    internal val weatherEngine: WeatherEngine = WeatherEngine()

    private val worldSimulationEngine: WorldSimulationEngine =
        WorldSimulationEngine(weatherEngine = weatherEngine)

    private val sceneUpdater: SceneUpdater = SceneUpdater()

    private val animationUpdater: AnimationUpdater = AnimationUpdater()

    private val particleUpdater: ParticleUpdater = ParticleUpdater()


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
     *
     * Time always reflects the device's actual clock when
     * there is no running world simulation to source it
     * from, so the theme stays in sync with real time of day.
     *
     * When the config carries a real location, the time-of-day
     * phase, moon appearance and season are derived from real
     * solar/lunar positions and hemisphere rather than fixed
     * defaults.
     *
     * [LightingState] is computed here — not inside Compose — so every
     * consumer (LightLayer, theme logic) always agrees on lighting.
     */
    fun generateTheme(config: AuraConfig): AuraTheme {

        val auraState =
            if (config.enabled) AuraState.ACTIVE else AuraState.OFF

        // When autoTime is true (default), always use real device clock.
        // When autoTime is false, use the simulated world time if available.
        val time: TimeState = if (config.autoTime) TimeState.now() else (world?.time ?: TimeState.now())

        val sunTimes =
            if (config.latitude != null && config.longitude != null)
                SolarCalculator.calculate(config.latitude, config.longitude)
            else
                null

        val timePhase =
            timePhaseEngine.calculate(
                time,
                sunTimes?.sunriseHour,
                sunTimes?.sunsetHour
            )

        val moonPhase = MoonPhaseCalculator.calculate()

        val season = SeasonEngine.calculate(config.latitude)

        // world.WeatherState is a typealias of model.WeatherState — no conversion needed.
        // If autoWeather is false, ignore any programmatically-set weather and fall back to CLEAR.
        val weatherState = if (config.autoWeather) {
            world?.weather ?: weatherEngine.refresh()
        } else {
            com.toblad.khwab.aura.model.WeatherState.CLEAR
        }

        // Compute authoritative lighting state here — not inside Compose layers.
        val lightingState = lightingEngine.update(time, weatherState)

        // ONE authoritative solar elevation for this theme — all visual layers read this
        // value from AuraTheme instead of computing it independently.
        val currentHour = time.hour + time.minute / 60f + time.second / 3600f
        val solarElevNorm = solarElevationNormPublic(
            currentHour = currentHour,
            sunriseHour = sunTimes?.sunriseHour,
            sunsetHour  = sunTimes?.sunsetHour
        )

        // ONE authoritative lunar illumination fraction — computed here once so SkyLayer,
        // StarLayer, and MoonLayer all receive the same value from AuraTheme rather than
        // each independently calling MoonPhaseCalculator.phaseFraction() inside a remember{}.
        // Formula: k = (1 − cos(2π·phase)) / 2   →   0 at new moon, 1 at full moon.
        val rawMoonPhase = MoonPhaseCalculator.phaseFraction()
        val moonIlluminationFraction = ((1.0 - cos(2.0 * PI * rawMoonPhase)) / 2.0).toFloat()

        return themeEngine.createTheme(
            auraState = auraState,
            timePhase = timePhase,
            weatherState = weatherState,
            enabled = config.enabled,
            moonPhase = moonPhase,
            season = season,
            stormIntensity = config.stormIntensity,
            sunriseHour = sunTimes?.sunriseHour,
            sunsetHour = sunTimes?.sunsetHour,
            lightingState = lightingState,
            animationsEnabled = config.animationsEnabled,
            solarElevNorm = solarElevNorm,
            moonIlluminationFraction = moonIlluminationFraction
        )
    }

    /**
     * Supplies fresh, real-world weather (e.g. fetched from
     * a weather provider using the device's location) to be
     * used the next time a theme is generated.
     */
    fun updateWeather(weather: com.toblad.khwab.aura.model.WeatherState) {
        // world.WeatherState is a typealias of model.WeatherState — direct pass-through
        weatherEngine.updateWeather(weather)
    }

    /**
     * Refreshes and returns the current AuraTheme.
     */
    fun refresh(config: AuraConfig): AuraTheme {
        return generateTheme(config)
    }
}