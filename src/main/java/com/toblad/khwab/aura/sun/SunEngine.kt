package com.toblad.khwab.aura.sun

import com.toblad.khwab.aura.world.TimeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculates the sun position from the current
 * simulated world time.
 *
 * The returned coordinates are normalized:
 *
 * X = 0.0 .. 1.0
 * Y = 0.0 .. 1.0
 */
class SunEngine {

    data class SunPosition(

        val x: Float,

        val y: Float,

        /**
         * Rotation angle in degrees.
         */
        val angle: Float
    )

    /**
     * Calculates the current sun position.
     */
    fun calculate(
        time: TimeState
    ): SunPosition {

        val seconds =
            time.hour * 3600 +
            time.minute * 60 +
            time.second

        val progress =
            seconds.toFloat() / (24f * 3600f)

        val angle =
            progress * (2f * PI.toFloat())

        val x =
            ((cos(angle - PI.toFloat() / 2f) + 1f) / 2f)

        val y =
            ((sin(angle - PI.toFloat() / 2f) + 1f) / 2f)

        return SunPosition(
            x = x,
            y = y,
            angle = Math.toDegrees(angle.toDouble()).toFloat()
        )
    }
}
