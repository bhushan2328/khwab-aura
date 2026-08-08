package com.toblad.khwab.aura.sun

import com.toblad.khwab.aura.world.TimeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Calculates the sun position from the current simulated world time.
 *
 * When real sunrise/sunset hours are available the sun traces a
 * half-ellipse arc that rises from the left horizon at sunrise,
 * peaks near the top of the screen at solar noon, and descends to
 * the right horizon at sunset — matching the actual sky.
 *
 * Without solar times the previous full-circle fallback is used so
 * existing behaviour is preserved when location is unavailable.
 *
 * The returned coordinates are normalised:
 *   X = 0.0 (left) .. 1.0 (right)
 *   Y = 0.0 (top)  .. 1.0 (bottom)
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
     *
     * @param time        Current world time.
     * @param sunriseHour Optional real sunrise as decimal hour (e.g. 6.5 = 06:30).
     * @param sunsetHour  Optional real sunset as decimal hour.
     */
    fun calculate(
        time: TimeState,
        sunriseHour: Float? = null,
        sunsetHour: Float? = null
    ): SunPosition {

        val fractionalHour = time.hour + time.minute / 60f + time.second / 3600f

        // Solar arc: use real sunrise/sunset when available.
        if (sunriseHour != null && sunsetHour != null && sunsetHour > sunriseHour) {
            return calculateSolarArc(fractionalHour, sunriseHour, sunsetHour)
        }

        // Fallback: full-circle approximation (original behaviour).
        return calculateCircle(time)
    }

    /**
     * Maps the current time to a half-ellipse arc that spans from
     * the left horizon (sunrise) to the right horizon (sunset).
     * The arc peaks at the top of the usable sky region (y ≈ 0.05)
     * at solar noon and sits on the horizon band (y ≈ 0.82) at the
     * edges, matching the bloom ellipse in SunLayer.
     */
    private fun calculateSolarArc(
        fractionalHour: Float,
        sunriseHour: Float,
        sunsetHour: Float
    ): SunPosition {

        val dayLength = sunsetHour - sunriseHour
        // t = 0 at sunrise, 1 at sunset; clamped so we never render the sun underground
        val t = ((fractionalHour - sunriseHour) / dayLength).coerceIn(0f, 1f)

        // Half-ellipse: angle goes from π (left) → 0 (right) as t goes 0 → 1
        val arcAngle = PI.toFloat() * (1f - t)

        // x: 0 (left horizon at sunrise) → 1 (right horizon at sunset)
        val x = (1f - t)                         // cos(arcAngle) mapped to 0..1 is identical

        // y: horizon (0.82) at edges, zenith (0.05) at noon
        // sin peaks at t=0.5, so y dips toward the top of the screen at noon
        val y = 0.82f - sin(arcAngle) * 0.77f    // 0.82 - 0.77 = 0.05 at noon

        return SunPosition(
            x     = x,
            y     = y,
            angle = Math.toDegrees(arcAngle.toDouble()).toFloat()
        )
    }

    private fun calculateCircle(time: TimeState): SunPosition {

        val seconds = time.hour * 3600 + time.minute * 60 + time.second
        val progress = seconds.toFloat() / (24f * 3600f)
        val angle = progress * (2f * PI.toFloat())
        val x = (cos(angle - PI.toFloat() / 2f) + 1f) / 2f
        val y = (sin(angle - PI.toFloat() / 2f) + 1f) / 2f

        return SunPosition(
            x     = x,
            y     = y,
            angle = Math.toDegrees(angle.toDouble()).toFloat()
        )
    }
}
