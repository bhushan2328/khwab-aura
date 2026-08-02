package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.world.TimeState

/**
 * Advances the simulated world time.
 *
 * The engine operates on a 24-hour clock and
 * will later support configurable time scaling.
 */
class TimeEngine(

    /**
     * Number of simulated seconds advanced
     * per real second.
     */
    private val timeScale: Float = 60f
) {

    /**
     * Advances the supplied world time by one frame.
     */
    fun update(
        time: TimeState,
        clock: FrameClock
    ): TimeState {

        var totalSeconds =
            time.hour * 3600 +
            time.minute * 60 +
            time.second

        totalSeconds += (clock.deltaTime * timeScale).toInt()

        totalSeconds %= 24 * 3600

        val hour = totalSeconds / 3600
        val minute = (totalSeconds % 3600) / 60
        val second = totalSeconds % 60

        return TimeState(
            hour = hour,
            minute = minute,
            second = second
        )
    }
}
