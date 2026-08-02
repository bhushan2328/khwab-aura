package com.toblad.khwab.aura.scene

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Represents the complete immutable visual scene.
 *
 * Every renderable object is stored as a [SceneNode].
 * AuraRenderer receives a SceneGraph and renders
 * every node in rendering order.
 *
 * SceneGraph never mutates.
 * Any modification returns a new SceneGraph.
 * ------------------------------------------------------------------
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
     * Future nodes...
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

    /**
     * Returns true if the graph contains
     * at least one node of the requested type.
     */
    inline fun <reified T : SceneNode> has(): Boolean =
        nodes.any { it is T }

    /**
     * Returns the number of nodes of the requested type.
     */
    inline fun <reified T : SceneNode> count(): Int =
        nodes.count { it is T }

    /**
     * Returns true if no nodes exist.
     */
    fun isEmpty(): Boolean =
        nodes.isEmpty()

    /**
     * Returns true if at least one node exists.
     */
    fun isNotEmpty(): Boolean =
        nodes.isNotEmpty()

    /**
     * Returns a new SceneGraph with the supplied node appended.
     */
    operator fun plus(node: SceneNode): SceneGraph =
        copy(nodes = nodes + node)

    /**
     * Returns a new SceneGraph with the supplied nodes appended.
     */
    operator fun plus(other: Collection<SceneNode>): SceneGraph =
        copy(nodes = nodes + other)

    /**
     * Returns a new SceneGraph without the first node
     * matching the supplied id.
     */
    operator fun minus(id: String): SceneGraph =
        copy(nodes = nodes.filterNot { it.id == id })

    /**
     * Finds a node by its stable identifier.
     */
    fun node(id: String): SceneNode? =
        nodes.firstOrNull { it.id == id }

    /**
     * Returns true if a node with the supplied id exists.
     */
    fun contains(id: String): Boolean =
        node(id) != null
}