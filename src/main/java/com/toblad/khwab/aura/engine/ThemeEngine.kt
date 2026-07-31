package com.toblad.khwab.aura.engine

import com.toblad.khwab.aura.model.*

/**
 * Responsible for converting the current
 * environment into a renderable AuraTheme.
 */
class ThemeEngine {

    fun createTheme(
        auraState: AuraState,
        timePhase: TimePhase,
        weatherState: WeatherState,
        enabled: Boolean
    ): AuraTheme {

        val profile = createProfile(timePhase, weatherState)

        return AuraTheme(
            auraState = auraState,
            timePhase = timePhase,
            weatherState = weatherState,
            profile = profile,
            enabled = enabled
        )
    }

    private fun createProfile(
        timePhase: TimePhase,
        weatherState: WeatherState
    ): ThemeProfile {

        val sky = when (timePhase) {
            TimePhase.MORNING -> SkyStyle.MORNING
            TimePhase.AFTERNOON -> SkyStyle.AFTERNOON
            TimePhase.EVENING -> SkyStyle.SUNSET
            TimePhase.NIGHT -> SkyStyle.NIGHT
        }

        val sun = when (timePhase) {
            TimePhase.MORNING -> SunStyle.MORNING
            TimePhase.AFTERNOON -> SunStyle.AFTERNOON
            TimePhase.EVENING -> SunStyle.SETTING
            TimePhase.NIGHT -> SunStyle.HIDDEN
        }

        val moon = when (timePhase) {
            TimePhase.NIGHT -> MoonStyle.FULL
            else -> MoonStyle.HIDDEN
        }

        val ambientLight = when (timePhase) {
            TimePhase.MORNING -> AmbientLightStyle.MORNING
            TimePhase.AFTERNOON -> AmbientLightStyle.AFTERNOON
            TimePhase.EVENING -> AmbientLightStyle.SUNSET
            TimePhase.NIGHT -> AmbientLightStyle.MOONLIGHT
        }

        val clouds = when (weatherState) {
            WeatherState.CLEAR -> CloudStyle.CLEAR
            WeatherState.CLOUDY -> CloudStyle.CLOUDY
            WeatherState.RAINY -> CloudStyle.RAIN
            WeatherState.STORM -> CloudStyle.STORM
        }

        val weatherEffect = when (weatherState) {
            WeatherState.CLEAR -> WeatherEffectStyle.NONE
            WeatherState.CLOUDY -> WeatherEffectStyle.NONE
            WeatherState.RAINY -> WeatherEffectStyle.LIGHT_RAIN
            WeatherState.STORM -> WeatherEffectStyle.THUNDERSTORM
        }

        val animation = when (weatherState) {
            WeatherState.CLEAR -> AnimationStyle.CALM
            WeatherState.CLOUDY -> AnimationStyle.NORMAL
            WeatherState.RAINY -> AnimationStyle.RAIN
            WeatherState.STORM -> AnimationStyle.STORM
        }

        return ThemeProfile(
            sky = sky,
            clouds = clouds,
            sun = sun,
            moon = moon,
            weatherEffect = weatherEffect,
            ambientLight = ambientLight,
            animation = animation
        )
    }
}

