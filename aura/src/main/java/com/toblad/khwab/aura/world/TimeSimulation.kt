package com.toblad.khwab.aura.world

/**
 * ------------------------------------------------------------------
 * Khwab Aura
 * ------------------------------------------------------------------
 *
 * Updates the simulated time of the Aura world.
 *
 * This is the first concrete implementation of
 * WorldUpdater.
 *
 * Future versions may support:
 * - Real device time
 * - Accelerated simulation
 * - Time zones
 * - Day/night cycles
 * ------------------------------------------------------------------
 */
class TimeSimulation : WorldUpdater {

    override fun update(
        world: AuraWorld,
        deltaSeconds: Float
    ): AuraWorld {

        val current = world.time

        var hour = current.hour
        var minute = current.minute
        var second = current.second + deltaSeconds.toInt()

        while (second >= 60) {
            second -= 60
            minute++
        }

        while (minute >= 60) {
            minute -= 60
            hour++
        }

        while (hour >= 24) {
            hour -= 24
        }

        return world.copy(
            time = current.copy(
                hour = hour,
                minute = minute,
                second = second
            )
        )
    }
}
