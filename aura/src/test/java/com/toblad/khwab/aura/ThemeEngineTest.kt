package com.toblad.khwab.aura

import com.toblad.khwab.aura.engine.ThemeEngine
import com.toblad.khwab.aura.model.*
import com.toblad.khwab.aura.world.LightingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ThemeEngine].
 */
class ThemeEngineTest {

    private val engine = ThemeEngine()

    // ── Night + Clear ──────────────────────────────────────────────────────────

    @Test
    fun `night + clear has NIGHT sky`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NIGHT,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertEquals(SkyStyle.NIGHT, theme.profile.sky)
    }

    @Test
    fun `night hides the sun`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NIGHT,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertEquals(SunStyle.HIDDEN, theme.profile.sun)
    }

    @Test
    fun `night shows moon with specified phase`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NIGHT,
            weatherState = WeatherState.CLEAR,
            enabled = true,
            moonPhase = MoonStyle.FULL
        )
        assertEquals(MoonStyle.FULL, theme.profile.moon)
    }

    // ── Day + Clear ────────────────────────────────────────────────────────────

    @Test
    fun `noon + clear has NOON sky`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertEquals(SkyStyle.NOON, theme.profile.sky)
    }

    @Test
    fun `noon shows sun`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertEquals(SunStyle.NOON, theme.profile.sun)
    }

    @Test
    fun `noon hides moon`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertEquals(MoonStyle.HIDDEN, theme.profile.moon)
    }

    // ── Rain ──────────────────────────────────────────────────────────────────

    @Test
    fun `rain weather has RAIN weather effect`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.MORNING,
            weatherState = WeatherState.RAIN,
            enabled = true
        )
        assertEquals(WeatherEffectStyle.RAIN, theme.profile.weatherEffect)
    }

    @Test
    fun `rain weather has OVERCAST clouds`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.MORNING,
            weatherState = WeatherState.RAIN,
            enabled = true
        )
        assertEquals(CloudStyle.OVERCAST, theme.profile.clouds)
    }

    // ── Snow ──────────────────────────────────────────────────────────────────

    @Test
    fun `snow weather has SNOW weather effect`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.AFTERNOON,
            weatherState = WeatherState.SNOW,
            enabled = true
        )
        assertEquals(WeatherEffectStyle.SNOW, theme.profile.weatherEffect)
    }

    // ── Storm ─────────────────────────────────────────────────────────────────

    @Test
    fun `storm weather has STORM weather effect`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.AFTERNOON,
            weatherState = WeatherState.STORM,
            enabled = true
        )
        assertEquals(WeatherEffectStyle.STORM, theme.profile.weatherEffect)
    }

    @Test
    fun `storm weather has STORM cloud style`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.AFTERNOON,
            weatherState = WeatherState.STORM,
            enabled = true
        )
        assertEquals(CloudStyle.STORM, theme.profile.clouds)
    }

    @Test
    fun `storm intensity propagates to profile`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.AFTERNOON,
            weatherState = WeatherState.STORM,
            enabled = true,
            stormIntensity = 0.9f
        )
        assertEquals(0.9f, theme.profile.stormIntensity, 0.001f)
    }

    // ── Lighting state passthrough ─────────────────────────────────────────────

    @Test
    fun `lighting state is propagated to theme`() {
        val customLighting = LightingState(intensity = 0.42f, ambient = 0.42f)
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true,
            lightingState = customLighting
        )
        assertEquals(0.42f, theme.lighting.intensity, 0.001f)
    }

    // ── animationsEnabled passthrough ─────────────────────────────────────────

    @Test
    fun `animationsEnabled false propagates to theme`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true,
            animationsEnabled = false
        )
        assertFalse(theme.animationsEnabled)
    }

    @Test
    fun `animationsEnabled true is default`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.NOON,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertTrue(theme.animationsEnabled)
    }

    // ── isSolarAccurate ────────────────────────────────────────────────────────

    @Test
    fun `isSolarAccurate true when sunrise and sunset provided`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.MORNING,
            weatherState = WeatherState.CLEAR,
            enabled = true,
            sunriseHour = 6.5f,
            sunsetHour = 19.5f
        )
        assertTrue(theme.isSolarAccurate)
    }

    @Test
    fun `isSolarAccurate false when no solar data`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.MORNING,
            weatherState = WeatherState.CLEAR,
            enabled = true
        )
        assertFalse(theme.isSolarAccurate)
    }

    // ── Season propagation ─────────────────────────────────────────────────────

    @Test
    fun `season is propagated to profile`() {
        val theme = engine.createTheme(
            auraState = AuraState.ACTIVE,
            timePhase = TimePhase.MORNING,
            weatherState = WeatherState.CLEAR,
            enabled = true,
            season = Season.WINTER
        )
        assertEquals(Season.WINTER, theme.profile.season)
    }
}
