package com.toblad.khwab.aura.scene

/**
 * Base interface for every renderable object
 * in the Aura Scene Graph.
 *
 * Each implementation represents a single
 * visual element that can be rendered by
 * AuraRenderer.
 */
interface SceneNode {

    /**
     * Stable identifier for this node.
     */
    val id: String
}

