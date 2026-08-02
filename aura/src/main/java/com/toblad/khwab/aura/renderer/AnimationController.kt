package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SceneGraph

/**
 * Coordinates scene animations.
 *
 * Future versions will manage animation timing,
 * speed, transitions and synchronization between
 * all renderers.
 */
class AnimationController {

    /**
     * Applies animations to the current scene.
     *
     * Currently returns the scene unchanged.
     * Future versions will animate nodes within
     * the SceneGraph.
     */
    fun apply(
        context: RenderContext
    ): SceneGraph {

        return context.scene
    }
}

