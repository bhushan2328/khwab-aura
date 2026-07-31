package com.toblad.khwab.aura.scene

/**
 * Represents the complete visual scene.
 *
 * Every renderable object is stored as a
 * SceneNode. AuraRenderer receives a
 * SceneGraph and renders all nodes.
 */
data class SceneGraph(

    /**
     * Ordered collection of renderable nodes.
     *
     * Rendering order:
     * Sky
     * Clouds
     * Sun
     * Moon
     * Weather
     * Lighting
     * Future objects
     */
    val nodes: List<SceneNode> = emptyList()

) {

    /**
     * Returns all nodes of the requested type.
     */
    inline fun <reified T : SceneNode> nodesOfType(): List<T> =
        nodes.filterIsInstance<T>()

    /**
     * Returns the first node of the requested type.
     */
    inline fun <reified T : SceneNode> firstNode(): T? =
        nodes.filterIsInstance<T>().firstOrNull()
}
