package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.*
import com.toblad.khwab.aura.world.LightingState

/**
 * Responsible for converting the current
 * environment into a renderable AuraTheme.
 */
class ThemeEngine {

    fun createTheme(
        auraState: AuraState,
        timePhase: TimePhase,
        weatherState: WeatherState,
        enabled: Boolean,
        moonPhase: MoonStyle = MoonStyle.FULL,
        season: Season = Season.SUMMER,
        stormIntensity: Float = 0.5f,
        sunriseHour: Float? = null,
        sunsetHour: Float? = null,
        lightingState: LightingState = LightingState(),
        animationsEnabled: Boolean = true
    ): AuraTheme {

        val profile = createProfile(timePhase, weatherState, moonPhase, season, stormIntensity)

        return AuraTheme(
            auraState = auraState,
            timePhase = timePhase,
            weatherState = weatherState,
            profile = profile,
            enabled = enabled,
            sunriseHour = sunriseHour,
            sunsetHour = sunsetHour,
            isSolarAccurate = sunriseHour != null && sunsetHour != null,
            lighting = lightingState,
            animationsEnabled = animationsEnabled
        )
    }

    private fun createProfile(
        timePhase: TimePhase,
        weatherState: WeatherState,
        moonPhase: MoonStyle,
        season: Season,
        stormIntensity: Float
    ): ThemeProfile {

        val sky = when (timePhase) {
            TimePhase.PRE_DAWN  -> SkyStyle.DAWN      // dark-navy → burgundy → warm-peach: true pre-dawn
            TimePhase.SUNRISE   -> SkyStyle.SUNRISE    // golden → coral → pink: full sunrise burst
            TimePhase.MORNING   -> SkyStyle.MORNING    // light blue + warm horizon
            TimePhase.NOON      -> SkyStyle.NOON
            TimePhase.AFTERNOON -> SkyStyle.AFTERNOON
            TimePhase.SUNSET    -> SkyStyle.SUNSET
            TimePhase.EVENING   -> SkyStyle.EVENING
            TimePhase.NIGHT     -> SkyStyle.NIGHT
            TimePhase.MIDNIGHT  -> SkyStyle.MIDNIGHT
        }

        val sun = when (timePhase) {
            TimePhase.PRE_DAWN -> SunStyle.HIDDEN
            TimePhase.SUNRISE -> SunStyle.DAWN
            TimePhase.MORNING -> SunStyle.MORNING
            TimePhase.NOON -> SunStyle.NOON
            TimePhase.AFTERNOON -> SunStyle.AFTERNOON
            TimePhase.SUNSET -> SunStyle.SUNSET
            TimePhase.EVENING -> SunStyle.HIDDEN
            TimePhase.NIGHT -> SunStyle.HIDDEN
            TimePhase.MIDNIGHT -> SunStyle.HIDDEN
        }

        val moon = when (timePhase) {
            TimePhase.NIGHT,
            TimePhase.MIDNIGHT,
            TimePhase.EVENING,
            TimePhase.PRE_DAWN -> moonPhase
            else -> MoonStyle.HIDDEN
        }

        val ambientLight = when (timePhase) {
            TimePhase.PRE_DAWN -> AmbientLightStyle.PRE_DAWN
            TimePhase.SUNRISE -> AmbientLightStyle.SUNRISE
            TimePhase.MORNING -> AmbientLightStyle.MORNING
            TimePhase.NOON -> AmbientLightStyle.NOON
            TimePhase.AFTERNOON -> AmbientLightStyle.AFTERNOON
            TimePhase.SUNSET -> AmbientLightStyle.SUNSET
            TimePhase.EVENING -> AmbientLightStyle.EVENING
            TimePhase.NIGHT -> AmbientLightStyle.MOONLIGHT
            TimePhase.MIDNIGHT -> AmbientLightStyle.NIGHT
        }

        val clouds = when (weatherState) {
            WeatherState.CLEAR  -> CloudStyle.CLEAR
            WeatherState.CLOUDY -> CloudStyle.SCATTERED  // was BROKEN — SCATTERED is more realistic for partly-cloudy
            WeatherState.RAIN   -> CloudStyle.OVERCAST
            WeatherState.SNOW   -> CloudStyle.OVERCAST
            WeatherState.FOG    -> CloudStyle.OVERCAST
            WeatherState.STORM  -> CloudStyle.STORM
        }

        val weatherEffect = when (weatherState) {
            WeatherState.CLEAR -> WeatherEffectStyle.NONE
            WeatherState.CLOUDY -> WeatherEffectStyle.NONE
            WeatherState.RAIN -> WeatherEffectStyle.RAIN
            WeatherState.SNOW -> WeatherEffectStyle.SNOW
            WeatherState.FOG -> WeatherEffectStyle.FOG
            WeatherState.STORM -> WeatherEffectStyle.STORM
        }

        val animation = when {
            // Clear sky wind budget by time of day:
            //   MORNING   → BREEZY (0.25) — fresh morning air
            //   NOON      → NORMAL (0.40) — peak convective wind from surface heating
            //   AFTERNOON → BREEZY (0.25) — wind eases as heating decreases
            // Night/pre-dawn stays CALM.
            weatherState == WeatherState.CLEAR && timePhase == TimePhase.NOON ->
                AnimationStyle.NORMAL
            weatherState == WeatherState.CLEAR &&
                    (timePhase == TimePhase.MORNING || timePhase == TimePhase.AFTERNOON) ->
                AnimationStyle.BREEZY
            weatherState == WeatherState.CLEAR -> AnimationStyle.CALM
            weatherState == WeatherState.CLOUDY -> AnimationStyle.NORMAL
            weatherState == WeatherState.RAIN -> AnimationStyle.RAIN
            weatherState == WeatherState.SNOW -> AnimationStyle.SNOW
            weatherState == WeatherState.FOG -> AnimationStyle.BREEZY
            weatherState == WeatherState.STORM -> AnimationStyle.STORM
            else -> AnimationStyle.CALM
        }

        return ThemeProfile(
            sky = sky,
            clouds = clouds,
            sun = sun,
            moon = moon,
            weatherEffect = weatherEffect,
            ambientLight = ambientLight,
            animation = animation,
            season = season,
            stormIntensity = stormIntensity
        )
    }
}