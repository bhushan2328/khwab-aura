package com.toblad.khwab.aura.model

/**
 * Represents the lifecycle state of the Khwab Aura Engine.
 *
 * Every Aura component should rely on this state.
 */
enum class AuraState {

    /**
     * Aura is disabled.
     */
    OFF,

    /**
     * Aura is preparing resources.
     */
    STARTING,

    /**
     * Aura is fully operational.
     */
    ACTIVE,

    /**
     * Aura is temporarily paused.
     */
    PAUSED,

    /**
     * Aura is shutting down.
     */
    STOPPING,

    /**
     * Aura encountered a fatal error.
     */
    ERROR
}
