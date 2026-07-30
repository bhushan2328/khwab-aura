package com.toblad.khwab.aura

import com.toblad.khwab.aura.api.AuraApi
import com.toblad.khwab.aura.manager.AuraManager

/**
 * Public entry point for the Aura module.
 *
 * The application should obtain the Aura API
 * through this singleton.
 */
object AuraModule {

    /**
     * Shared Aura instance.
     */
    private val manager: AuraApi by lazy {
        AuraManager()
    }

    /**
     * Returns the shared Aura API.
     */
    fun get(): AuraApi = manager
}
