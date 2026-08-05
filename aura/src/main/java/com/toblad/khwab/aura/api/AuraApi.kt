package com.toblad.khwab.aura.api

import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.WeatherState

/**
 * Public contract for the Khwab Aura module.
 *
 * Applications communicate with Aura only
 * through this interface.
 */
interface AuraApi {

    /**
     * Enables Aura.
     */
    fun activate()

    /**
     * Disables Aura.
     */
    fun deactivate()

    /**
     * Toggles Aura.
     */
    fun toggle()

    /**
     * Returns true if Aura is active.
     */
    fun isActive(): Boolean

    /**
     * Returns the current Aura state.
     */
    fun getState(): AuraState

    /**
     * Returns the current Aura theme.
     */
    fun getTheme(): AuraTheme

    /**
     * Returns the current configuration.
     */
    fun getConfig(): AuraConfig

    /**
     * Replaces the current configuration.
     */
    fun updateConfig(config: AuraConfig)

    /**
     * Supplies Aura with the latest real-world weather
     * conditions (e.g. fetched from a weather provider using
     * the device's current location).
     */
    fun updateWeather(weather: WeatherState)

    /**
     * Refreshes Aura using the latest
     * environmental information.
     */
    fun refresh()
}