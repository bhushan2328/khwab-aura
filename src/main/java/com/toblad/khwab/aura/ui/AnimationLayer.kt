package com.toblad.khwab.aura.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.toblad.khwab.aura.model.AnimationStyle
import com.toblad.khwab.aura.model.AuraTheme

/**
 * Coordinates scene-wide animations.
 *
 * This milestone establishes the animation
 * entry point. Future milestones will add
 * Compose animations and transition effects.
 */
@Composable
fun AnimationLayer(
    theme: AuraTheme
) {

    LaunchedEffect(theme.profile.animation) {

        when (theme.profile.animation) {

            AnimationStyle.NONE -> Unit

            AnimationStyle.CALM -> Unit

            AnimationStyle.BREEZY -> Unit

            AnimationStyle.NORMAL -> Unit

            AnimationStyle.WINDY -> Unit

            AnimationStyle.RAIN -> Unit

            AnimationStyle.SNOW -> Unit

            AnimationStyle.STORM -> Unit
        }
    }
}

