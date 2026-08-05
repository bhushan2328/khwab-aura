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
 */
class AuraManager : AuraApi {

    private val engine = AuraEngine()

    private var state = AuraState.OFF

    private var config = AuraConfig()

    private var theme = engine.generateTheme(config)

    override fun activate() {

        state = AuraState.ACTIVE

        config = config.copy(enabled = true)

        theme = engine.generateTheme(config)
    }

    override fun deactivate() {

        state = AuraState.OFF

        config = config.copy(enabled = false)

        theme = engine.generateTheme(config)
    }

    override fun toggle() {

        if (isActive()) {
            deactivate()
        } else {
            activate()
        }
    }

    override fun isActive(): Boolean {
        return state == AuraState.ACTIVE
    }

    override fun getState(): AuraState {
        return state
    }

    override fun getTheme(): AuraTheme {
        return theme
    }

    override fun getConfig(): AuraConfig {
        return config
    }

    override fun updateConfig(config: AuraConfig) {

        this.config = config

        theme = engine.generateTheme(config)
    }

    override fun updateWeather(weather: WeatherState) {

        engine.updateWeather(weather)

        theme = engine.generateTheme(config)
    }

    override fun refresh() {

        theme = engine.refresh(config)
    }
}