package com.toblad.khwab.aura.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Tracks whether the current screen is actually visible and
 * resumed (foreground, not covered, not backgrounded).
 *
 * Used to pause Aura's animation loops and periodic sun/moon
 * position polling when nobody can see them, to avoid wasted
 * battery. Cheap, event-driven work (LightningBus, ambient
 * sound) deliberately does NOT use this — that should keep
 * running in the background like any ambient-audio app.
 */
@Composable
fun rememberIsResumed(): State<Boolean> {

    val lifecycleOwner = LocalLifecycleOwner.current

    val state = remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.value = true
                Lifecycle.Event.ON_PAUSE -> state.value = false
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}