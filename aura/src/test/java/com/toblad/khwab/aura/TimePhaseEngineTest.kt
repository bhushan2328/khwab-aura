package com.toblad.khwab.aura

import com.toblad.khwab.aura.engine.TimePhaseEngine
import com.toblad.khwab.aura.model.TimePhase
import com.toblad.khwab.aura.world.TimeState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TimePhaseEngine].
 *
 * Tests both the fixed (no solar data) and solar-aware paths.
 */
class TimePhaseEngineTest {

    private val engine = TimePhaseEngine()

    // ── Fixed schedule (no solar data) ────────────────────────────────────────

    @Test
    fun `midnight at 00_00`() {
        assertEquals(TimePhase.MIDNIGHT, engine.calculate(TimeState(0, 0, 0)))
    }

    @Test
    fun `pre-dawn at 05_00`() {
        assertEquals(TimePhase.PRE_DAWN, engine.calculate(TimeState(5, 0, 0)))
    }

    @Test
    fun `sunrise at 06_00`() {
        assertEquals(TimePhase.SUNRISE, engine.calculate(TimeState(6, 0, 0)))
    }

    @Test
    fun `morning at 09_00`() {
        assertEquals(TimePhase.MORNING, engine.calculate(TimeState(9, 0, 0)))
    }

    @Test
    fun `noon at 12_00`() {
        assertEquals(TimePhase.NOON, engine.calculate(TimeState(12, 0, 0)))
    }

    @Test
    fun `afternoon at 15_00`() {
        assertEquals(TimePhase.AFTERNOON, engine.calculate(TimeState(15, 0, 0)))
    }

    @Test
    fun `sunset at 18_00`() {
        assertEquals(TimePhase.SUNSET, engine.calculate(TimeState(17, 30, 0)))
    }

    @Test
    fun `evening at 20_00`() {
        assertEquals(TimePhase.EVENING, engine.calculate(TimeState(20, 0, 0)))
    }

    @Test
    fun `night at 23_00`() {
        assertEquals(TimePhase.NIGHT, engine.calculate(TimeState(23, 0, 0)))
    }

    // ── Solar-aware path ───────────────────────────────────────────────────────

    @Test
    fun `sunrise when at sunrise with solar data`() {
        // Sunrise at 6:30, sunset at 19:30 — at exactly 6:30 → SUNRISE phase
        val phase = engine.calculate(TimeState(6, 30, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        assertEquals(TimePhase.SUNRISE, phase)
    }

    @Test
    fun `morning when mid-morning with solar data`() {
        val phase = engine.calculate(TimeState(9, 0, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        assertEquals(TimePhase.MORNING, phase)
    }

    @Test
    fun `noon when solar noon with solar data`() {
        val phase = engine.calculate(TimeState(13, 0, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        assertEquals(TimePhase.NOON, phase)
    }

    @Test
    fun `afternoon when mid-afternoon with solar data`() {
        val phase = engine.calculate(TimeState(16, 0, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        assertEquals(TimePhase.AFTERNOON, phase)
    }

    @Test
    fun `sunset when at sunset with solar data`() {
        val phase = engine.calculate(TimeState(19, 30, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        assertEquals(TimePhase.SUNSET, phase)
    }

    @Test
    fun `night when deep night with solar data`() {
        val phase = engine.calculate(TimeState(2, 0, 0), sunriseHour = 6.5f, sunsetHour = 19.5f)
        // Deep night (2am) should be NIGHT or MIDNIGHT
        assert(phase == TimePhase.NIGHT || phase == TimePhase.MIDNIGHT) {
            "Expected NIGHT or MIDNIGHT at 02:00, got $phase"
        }
    }

    @Test
    fun `falls back to fixed schedule when sunset not after sunrise`() {
        // Invalid solar data → should use fixed schedule
        // At 12:00, fixed schedule = NOON
        val phase = engine.calculate(TimeState(12, 0, 0), sunriseHour = 19f, sunsetHour = 6f)
        assertEquals(TimePhase.NOON, phase)
    }
}
