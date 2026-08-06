package com.toblad.khwab.aura.sun

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Computes real sunrise and sunset times for a given
 * location and date using the standard sunrise equation.
 *
 * Pure math — no network access or third-party dependency
 * required.
 */
object SolarCalculator {

    data class SunTimes(
        val sunriseHour: Float,
        val sunsetHour: Float
    )

    /**
     * Returns local sunrise/sunset as decimal hours (e.g.
     * 6.5f = 06:30), or null if this location has no
     * sunrise/sunset on this date (polar day or polar night).
     */
    fun calculate(
        latitude: Double,
        longitude: Double,
        date: Calendar = Calendar.getInstance()
    ): SunTimes? {

        val julianDate = 2440588.0 + floor(date.timeInMillis / 86400000.0)

        val n = julianDate - 2451545.0 + 0.0008

        val jStar = n - longitude / 360.0

        val m = Math.toRadians((357.5291 + 0.98560028 * jStar).mod(360.0))

        val c = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)

        val lambda = Math.toRadians(
            (Math.toDegrees(m) + 102.9372 + c + 180.0).mod(360.0)
        )

        val jTransit =
            2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)

        val delta = asin(sin(lambda) * sin(Math.toRadians(23.44)))

        val latRad = Math.toRadians(latitude)

        val cosH =
            (sin(Math.toRadians(-0.83)) - sin(latRad) * sin(delta)) /
                    (cos(latRad) * cos(delta))

        if (cosH < -1.0 || cosH > 1.0) {
            // Polar day (always light) or polar night (always dark).
            return null
        }

        val h = Math.toDegrees(acos(cosH))

        val jRise = jTransit - h / 360.0
        val jSet = jTransit + h / 360.0

        val timeZoneOffsetHours =
            TimeZone.getDefault().getOffset(date.timeInMillis) / 3600000.0

        return SunTimes(
            sunriseHour = fractionalHour(jRise, timeZoneOffsetHours),
            sunsetHour = fractionalHour(jSet, timeZoneOffsetHours)
        )
    }

    private fun fractionalHour(
        julianDate: Double,
        timeZoneOffsetHours: Double
    ): Float {

        val dayFraction = (julianDate + 0.5).mod(1.0)

        val hour = ((dayFraction * 24.0 + timeZoneOffsetHours) % 24.0 + 24.0) % 24.0

        return hour.toFloat()
    }
}