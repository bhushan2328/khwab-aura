package com.toblad.khwab.aura.engine

/**
 * Represents timing information for one engine frame.
 */
data class FrameClock(

    val deltaTime: Float = 0f,

    val elapsedTime: Long = 0L
)

