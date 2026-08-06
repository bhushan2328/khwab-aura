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
import kotlin.random.Random

/**
 * Single shared source of truth for "when does lightning
 * strike."
 *
 * Both the visual flash (WeatherLayer, in this module) and
 * the thunder sound (AmbientSoundController, in the host app)
 * collect [flashes] instead of running their own independent
 * random timers — so they fire from the exact same event
 * instead of drifting apart.
 */
object LightningBus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _flashes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashes: SharedFlow<Unit> = _flashes.asSharedFlow()

    @Volatile
    private var severity: Float = 0.5f

    private var job: Job? = null

    /**
     * Call whenever the storm state or its severity changes.
     * Safe to call from multiple places (the visual layer and
     * the sound controller both call this) — the first caller
     * starts the ticker; later calls just update the severity
     * the running ticker uses.
     */
    fun update(stormActive: Boolean, intensity: Float) {

        severity = intensity.coerceIn(0f, 1f)

        if (!stormActive) {
            job?.cancel()
            job = null
            return
        }

        if (job?.isActive == true) return

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