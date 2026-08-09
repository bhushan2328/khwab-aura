package com.toblad.khwab.aura.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Single shared source of truth for "when does lightning strike."
 *
 * Both the visual flash (WeatherLayer, in this module) and
 * the thunder sound (AmbientSoundController, in the host app)
 * collect [flashes] instead of running their own independent
 * random timers — so they fire from the exact same event
 * instead of drifting apart.
 *
 * Thread safety:
 * - [severity] is @Volatile for lock-free reads from the ticker coroutine.
 * - [mutex] ensures only one call to [update] modifies [job] at a time,
 *   so concurrent callers can never accidentally start duplicate ticker jobs.
 */
object LightningBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _flashes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashes: SharedFlow<Unit> = _flashes.asSharedFlow()

    @Volatile
    private var severity: Float = 0.5f

    private var job: Job? = null

    /** Guards [job] mutations so concurrent callers cannot race. */
    private val mutex = Mutex()

    /**
     * Call whenever the storm state or its severity changes.
     * Safe to call from multiple places (the visual layer and
     * the sound controller both call this) — the first caller
     * starts the ticker; later calls just update the severity
     * the running ticker uses.
     *
     * When [stormActive] is false the current ticker job is cancelled
     * and cleaned up immediately, so no lightning fires after a storm ends.
     */
    fun update(stormActive: Boolean, intensity: Float) {
        severity = intensity.coerceIn(0f, 1f)

        scope.launch {
            mutex.withLock {
                if (!stormActive) {
                    job?.cancel()
                    job = null
                    return@withLock
                }

                if (job?.isActive == true) return@withLock

                job = scope.launch {
                    while (isActive) {
                        // Stronger storms produce more frequent lightning.
                        val minDelay =
                            (1500L - (severity * 800f).toLong())
                                .coerceAtLeast(600L)

                        val maxDelay =
                            (9000L - (severity * 4000f).toLong())
                                .coerceAtLeast(minDelay + 500L)

                        delay(Random.nextLong(minDelay, maxDelay))
                        _flashes.emit(Unit)
                    }
                }
            }
        }
    }

    /**
     * Cancels any active lightning ticker and resets state.
     *
     * Should be called when Aura is deactivated so no lightning jobs
     * continue running in the background unnecessarily.
     */
    fun reset() {
        scope.launch {
            mutex.withLock {
                job?.cancel()
                job = null
                severity = 0.5f
            }
        }
    }

    /**
     * Immediately emits one lightning flash event on [flashes].
     *
     * Intended for debug/testing use only — allows a single manual
     * flash without affecting the running ticker or storm state.
     */
    fun triggerNow() {
        scope.launch { _flashes.emit(Unit) }
    }
}
