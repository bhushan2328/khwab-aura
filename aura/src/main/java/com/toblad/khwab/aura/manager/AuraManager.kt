package com.toblad.khwab.aura.manager

import com.toblad.khwab.aura.api.AuraApi
import com.toblad.khwab.aura.engine.AuraEngine
import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.WeatherState
import com.toblad.khwab.aura.ui.LightningBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central controller for the Khwab Aura module.
 *
 * All Aura processing is delegated to AuraEngine.
 *
 * ## State ownership
 * - [themeFlow] is the single authoritative stream of the current visual theme.
 *   Compose UI should collect it; the synchronous [getTheme] is provided for
 *   non-reactive callers that need a snapshot.
 * - [config] and [state] are updated together under a synchronized lock so the
 *   three mutable fields (state, config, theme) are always mutually consistent.
 *
 * ## Thread safety
 * All mutable fields are @Volatile so reads from the UI thread and writes from
 * weather-callback coroutines never observe torn state. Mutations that must be
 * kept consistent are performed inside synchronized(lock).
 *
 * ## LightningBus lifecycle
 * [deactivate] calls [LightningBus.reset] so the lightning ticker is stopped
 * when Aura is not active. It will restart automatically the next time a storm
 * theme is displayed.
 *
 * ## Atmospheric refresh
 * The manager runs a low-frequency background coroutine (every 60 seconds) that
 * calls [refresh] so the atmospheric state — especially [AuraTheme.solarElevNorm]
 * — stays current with the real clock.  This replaces the per-layer 30-second
 * polls that existed inside SkyLayer and CloudLayer.
 *
 * The 60-second interval is deliberately conservative:
 *   • The sun moves ≈ 0.25° per minute — imperceptible in 60 s.
 *   • [AuraTheme.solarElevNorm] changes by < 0.001 per minute at most solar
 *     positions; the change is larger (≈ 0.002/min) only within ±30 min of
 *     sunrise/sunset, where it matters most.
 *   • [animateColorAsState(tween(3000))] in SkyLayer provides 3-second visual
 *     interpolation that conceals any step between refreshes.
 *   • No battery-draining busy loop is introduced — only 1 wakeup per minute.
 */
class AuraManager : AuraApi {

    private val engine = AuraEngine()

    private val lock = Any()

    @Volatile private var state  = AuraState.OFF
    @Volatile private var config = AuraConfig()

    private val _themeFlow = MutableStateFlow(engine.generateTheme(config))

    /** Reactive stream of the current Aura theme. Backed by [MutableStateFlow]. */
    override val themeFlow: StateFlow<AuraTheme> = _themeFlow.asStateFlow()

    /**
     * Scope for the periodic atmospheric refresh coroutine.
     * Uses [Dispatchers.Default] (background thread) for the computation,
     * but [MutableStateFlow] is thread-safe so the emit is safe from any thread.
     */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Start the low-frequency atmospheric refresh loop.
        // Fires every 60 seconds to keep solarElevNorm and lightingState current.
        // The loop checks [state] so it skips the work when Aura is inactive.
        refreshScope.launch {
            while (isActive) {
                delay(60_000L)
                synchronized(lock) {
                    _themeFlow.value = engine.refresh(config)
                }
            }
        }
    }

    override fun activate() = synchronized(lock) {
        state  = AuraState.ACTIVE
        config = config.copy(enabled = true)
        _themeFlow.value = engine.generateTheme(config)
    }

    override fun deactivate() = synchronized(lock) {
        state  = AuraState.OFF
        config = config.copy(enabled = false)
        _themeFlow.value = engine.generateTheme(config)
        // Stop the lightning ticker when Aura is not active.
        LightningBus.reset()
    }

    override fun toggle() {
        if (isActive()) deactivate() else activate()
    }

    override fun isActive(): Boolean = state == AuraState.ACTIVE

    override fun getState(): AuraState = state

    override fun getTheme(): AuraTheme = _themeFlow.value

    override fun getConfig(): AuraConfig = config

    override fun updateConfig(config: AuraConfig) = synchronized(lock) {
        this.config = config
        _themeFlow.value = engine.generateTheme(this.config)
    }

    override fun updateWeather(weather: WeatherState) = synchronized(lock) {
        engine.updateWeather(weather)
        _themeFlow.value = engine.generateTheme(config)
    }

    override fun refresh() = synchronized(lock) {
        _themeFlow.value = engine.refresh(config)
    }
}
