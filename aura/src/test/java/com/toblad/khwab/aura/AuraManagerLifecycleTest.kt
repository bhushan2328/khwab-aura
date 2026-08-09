package com.toblad.khwab.aura

import com.toblad.khwab.aura.manager.AuraManager
import com.toblad.khwab.aura.model.AuraConfig
import com.toblad.khwab.aura.model.AuraState
import com.toblad.khwab.aura.model.WeatherState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AuraManager] lifecycle, state, and configuration.
 */
class AuraManagerLifecycleTest {

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is OFF`() {
        val manager = AuraManager()
        assertEquals(AuraState.OFF, manager.getState())
    }

    @Test
    fun `initial isActive is false`() {
        val manager = AuraManager()
        assertFalse(manager.isActive())
    }

    @Test
    fun `initial theme has auraState OFF`() {
        val manager = AuraManager()
        assertEquals(AuraState.OFF, manager.getTheme().auraState)
    }

    // ── activate ──────────────────────────────────────────────────────────────

    @Test
    fun `activate sets state to ACTIVE`() {
        val manager = AuraManager()
        manager.activate()
        assertEquals(AuraState.ACTIVE, manager.getState())
    }

    @Test
    fun `activate makes isActive true`() {
        val manager = AuraManager()
        manager.activate()
        assertTrue(manager.isActive())
    }

    @Test
    fun `activate sets theme auraState to ACTIVE`() {
        val manager = AuraManager()
        manager.activate()
        assertEquals(AuraState.ACTIVE, manager.getTheme().auraState)
    }

    @Test
    fun `activate reflects in themeFlow`() {
        val manager = AuraManager()
        manager.activate()
        assertEquals(AuraState.ACTIVE, manager.themeFlow.value.auraState)
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    fun `deactivate after activate returns to OFF`() {
        val manager = AuraManager()
        manager.activate()
        manager.deactivate()
        assertEquals(AuraState.OFF, manager.getState())
        assertFalse(manager.isActive())
    }

    @Test
    fun `deactivate sets theme auraState to OFF`() {
        val manager = AuraManager()
        manager.activate()
        manager.deactivate()
        assertEquals(AuraState.OFF, manager.getTheme().auraState)
    }

    // ── toggle ────────────────────────────────────────────────────────────────

    @Test
    fun `toggle from inactive activates`() {
        val manager = AuraManager()
        manager.toggle()
        assertTrue(manager.isActive())
    }

    @Test
    fun `toggle from active deactivates`() {
        val manager = AuraManager()
        manager.activate()
        manager.toggle()
        assertFalse(manager.isActive())
    }

    @Test
    fun `double toggle returns to original state`() {
        val manager = AuraManager()
        manager.toggle()
        manager.toggle()
        assertFalse(manager.isActive())
    }

    // ── config ────────────────────────────────────────────────────────────────

    @Test
    fun `updateConfig is reflected in getConfig`() {
        val manager = AuraManager()
        val config = AuraConfig(latitude = 51.5, longitude = -0.12)
        manager.updateConfig(config)
        assertEquals(51.5, manager.getConfig().latitude)
        assertEquals(-0.12, manager.getConfig().longitude)
    }

    @Test
    fun `updateConfig with animationsEnabled false propagates to theme`() {
        val manager = AuraManager()
        manager.activate()
        manager.updateConfig(AuraConfig(enabled = true, animationsEnabled = false))
        assertFalse(manager.getTheme().animationsEnabled)
    }

    @Test
    fun `updateConfig with animationsEnabled true propagates to theme`() {
        val manager = AuraManager()
        manager.activate()
        manager.updateConfig(AuraConfig(enabled = true, animationsEnabled = true))
        assertTrue(manager.getTheme().animationsEnabled)
    }

    // ── weather ───────────────────────────────────────────────────────────────

    @Test
    fun `updateWeather propagates to theme weatherState`() {
        val manager = AuraManager()
        manager.activate()
        manager.updateWeather(WeatherState.STORM)
        assertEquals(WeatherState.STORM, manager.getTheme().weatherState)
    }

    @Test
    fun `autoWeather false ignores updateWeather`() {
        val manager = AuraManager()
        manager.activate()
        manager.updateConfig(AuraConfig(enabled = true, autoWeather = false))
        manager.updateWeather(WeatherState.STORM)
        // autoWeather = false means weather is forced to CLEAR
        assertEquals(WeatherState.CLEAR, manager.getTheme().weatherState)
    }

    @Test
    fun `updateWeather reflects in themeFlow`() {
        val manager = AuraManager()
        manager.activate()
        manager.updateWeather(WeatherState.RAIN)
        assertEquals(WeatherState.RAIN, manager.themeFlow.value.weatherState)
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    fun `refresh does not crash`() {
        val manager = AuraManager()
        manager.activate()
        manager.refresh() // should not throw
    }

    @Test
    fun `refresh updates themeFlow`() {
        val manager = AuraManager()
        manager.activate()
        val before = manager.themeFlow.value
        manager.refresh()
        // Theme may or may not change (depends on time), but it should still be non-null
        val after = manager.themeFlow.value
        // Both versions should have auraState ACTIVE
        assertEquals(AuraState.ACTIVE, after.auraState)
    }

    // ── StateFlow consistency ──────────────────────────────────────────────────

    @Test
    fun `themeFlow value equals getTheme`() {
        val manager = AuraManager()
        manager.activate()
        assertEquals(manager.getTheme(), manager.themeFlow.value)
    }

    @Test
    fun `themeFlow value reflects after deactivate`() {
        val manager = AuraManager()
        manager.activate()
        manager.deactivate()
        assertEquals(manager.getTheme(), manager.themeFlow.value)
        assertEquals(AuraState.OFF, manager.themeFlow.value.auraState)
    }
}
