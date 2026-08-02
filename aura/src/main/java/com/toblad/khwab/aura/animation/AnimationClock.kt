package com.toblad.khwab.aura.animation

/**
 * Simple animation timer used by Aura.
 *
 * Keeps track of elapsed animation time in seconds.
 */
class AnimationClock {

    private var elapsedTime = 0f

    /**
     * Advance the clock.
     */
    fun update(deltaSeconds: Float) {
        elapsedTime += deltaSeconds
    }

    /**
     * Returns elapsed time in seconds.
     */
    fun elapsedTime(): Float {
        return elapsedTime
    }

    /**
     * Reset animation time.
     */
    fun reset() {
        elapsedTime = 0f
    }
}