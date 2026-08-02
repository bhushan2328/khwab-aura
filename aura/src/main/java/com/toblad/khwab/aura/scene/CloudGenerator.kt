package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.model.CloudStyle

/**
 * Generates cloud nodes for the SceneGraph.
 *
 * Future versions will consider:
 * - Wind
 * - Time of day
 * - Weather
 * - Season
 * - Random distribution
 * - Cloud clustering
 */
class CloudGenerator {

    /**
     * Generates cloud nodes for the supplied style.
     */
    fun generate(
        style: CloudStyle
    ): List<CloudNode> {

        val count = when (style) {
            CloudStyle.CLEAR -> 0
            CloudStyle.FEW -> 2
            CloudStyle.SCATTERED -> 4
            CloudStyle.BROKEN -> 8
            CloudStyle.OVERCAST -> 12
            CloudStyle.STORM -> 16
        }

        return List(count) { index ->

            CloudNode(
                id = "cloud-${index + 1}",
                style = style,
                x = 0.10f + (index * 0.08f),
                y = 0.15f + ((index % 3) * 0.08f),
                scale = 0.8f + ((index % 4) * 0.2f)
            )
        }
    }
}