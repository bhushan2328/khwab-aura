package com.toblad.khwab.aura.scene

import com.toblad.khwab.aura.animation.AnimationClock
import kotlin.math.sin

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Converts AnimationClock time into a dynamic SceneState.
 *
 * Future versions will interpolate:
 * - Sun movement
 * - Moon movement
 * - Cloud movement
 * - Ambient lighting
 * - Weather intensity
 *
 * This class contains no rendering logic.
 * ------------------------------------------------------------------
 */
class SceneInterpolator(

    private val clock: AnimationClock = AnimationClock()

) {

    /**
     * Advances the animation clock.
     */
    fun update(
        deltaSeconds: Float
    ) {
        clock.update(deltaSeconds)
    }

    /**
     * Creates the current SceneState.
     */
    fun currentState(): SceneState {

        val time = clock.elapsedTime()

        return SceneState(

            dayProgress =
                ((sin(time * 0.02f) + 1f) * 0.5f),

            cloudOffset =
                time * 0.01f,

            lightIntensity =
                0.8f + 0.2f * sin(time * 0.02f),

            sunProgress =
                ((sin(time * 0.02f) + 1f) * 0.5f),

            moonProgress =
                1f - ((sin(time * 0.02f) + 1f) * 0.5f)

        )
    }

    /**
     * Resets the animation.
     */
    fun reset() {
        clock.reset()
    }
}
