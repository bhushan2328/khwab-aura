package com.toblad.khwab.aura.manager

import com.toblad.khwab.aura.api.AuraApi
import com.toblad.khwab.aura.engine.AuraEngine
import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.WeatherState

/**
 * Central controller for the Khwab Aura module.
 *
 * All Aura processing is delegated to AuraEngine.
 *
 * Thread safety: all mutable fields are @Volatile so reads from the UI
 * thread and writes from weather-callback coroutines never observe torn state.
 * The three fields (state, config, theme) are updated together under a
 * synchronized lock so they are always mutually consistent.
 */
class AuraManager : AuraApi {

    private val engine = AuraEngine()

    private val lock = Any()

    @Volatile private var state  = AuraState.OFF
    @Volatile private var config = AuraConfig()
    @Volatile private var theme  = engine.generateTheme(config)

    override fun activate() = synchronized(lock) {
        state  = AuraState.ACTIVE
        config = config.copy(enabled = true)
        theme  = engine.generateTheme(config)
    }

    override fun deactivate() = synchronized(lock) {
        state  = AuraState.OFF
        config = config.copy(enabled = false)
        theme  = engine.generateTheme(config)
    }

    override fun toggle() {
        if (isActive()) deactivate() else activate()
    }

    override fun isActive(): Boolean = state == AuraState.ACTIVE

    override fun getState(): AuraState = state

    override fun getTheme(): AuraTheme = theme

    override fun getConfig(): AuraConfig = config

    override fun updateConfig(config: AuraConfig) = synchronized(lock) {
        this.config = config
        theme = engine.generateTheme(this.config)
    }

    override fun updateWeather(weather: WeatherState) = synchronized(lock) {
        engine.updateWeather(weather)
        theme = engine.generateTheme(config)
    }

    override fun refresh() = synchronized(lock) {
        theme = engine.refresh(config)
    }
}
