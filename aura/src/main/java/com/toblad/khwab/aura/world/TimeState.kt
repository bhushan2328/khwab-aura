package com.toblad.khwab.aura.world

import java.util.Calendar

/**
 * Represents the current time within Aura.
 */
data class TimeState(

    val hour: Int = 12,

    val minute: Int = 0,

    val second: Int = 0
) {

    companion object {

        /**
         * Builds a [TimeState] from the device's actual
         * local clock. Used whenever Aura has no running
         * simulation to source time from, so the theme
         * always reflects the real time of day.
         */
        fun now(): TimeState {

            val calendar = Calendar.getInstance()

            return TimeState(
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                second = calendar.get(Calendar.SECOND)
            )
        }
    }
}