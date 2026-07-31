package com.toblad.khwab.aura.renderer

import com.toblad.khwab.aura.scene.SceneGraph

/**
 * Rendering context shared by all Aura renderers.
 *
 * Every renderer receives the same RenderContext,
 * ensuring a consistent rendering pipeline.
 *
 * Future versions will extend this class with:
 * - Screen size
 * - Density
 * - Canvas
 * - Time
 * - Animation progress
 * - Frame timing
 * - GPU resources
 */
data class RenderContext(

    /**
     * Scene to render.
     */
    val scene: SceneGraph

)

