package com.toblad.khwab.aura.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.toblad.khwab.aura.model.AuraTheme
import com.toblad.khwab.aura.model.SkyStyle

/**
 * Renders the background sky using vertical gradients that
 * smoothly cross-fade whenever the sky style changes.
 *
 * Each stop is animated individually with a named val instead
 * of calling animateColorAsState inside a loop, which violated
 * Compose's "no composable calls in lambdas" rule.
 */
@Composable
fun SkyLayer(theme: AuraTheme) {

    // Resolve the target gradient stops for the current sky style.
    // All daytime phases now have a three-stop gradient:
    //   top  = deep zenith blue
    //   mid  = main sky colour
    //   bot  = warm/pale horizon band so the sky doesn't look flat
    val (rawTop, rawMid, rawBot) = when (theme.profile.sky) {
        SkyStyle.PRE_DAWN   -> Triple(Color(0xFF03045E), Color(0xFF1D3557),  null)
        SkyStyle.DAWN       -> Triple(Color(0xFF1A1A3E), Color(0xFF8B3A62),  Color(0xFFFF9E80))
        SkyStyle.SUNRISE    -> Triple(Color(0xFFFFB703), Color(0xFFFF7F50),  Color(0xFFFFC8DD))
        SkyStyle.MORNING    -> Triple(Color(0xFF4FC3F7), Color(0xFF87CEEB),  Color(0xFFFFF8E1))  // warm horizon
        SkyStyle.NOON       -> Triple(Color(0xFF1565C0), Color(0xFF42A5F5),  Color(0xFFE3F2FD))  // deep zenith + pale horizon
        SkyStyle.AFTERNOON  -> Triple(Color(0xFF0D47A1), Color(0xFF42A5F5),  Color(0xFFFFF9C4))  // golden horizon
        SkyStyle.SUNSET     -> Triple(Color(0xFFFF7043), Color(0xFFAB47BC),  Color(0xFF5E35B1))
        SkyStyle.EVENING    -> Triple(Color(0xFF3949AB), Color(0xFF7986CB),  null)
        SkyStyle.NIGHT      -> Triple(Color(0xFF0D1B2A), Color(0xFF000814),  null)
        SkyStyle.MIDNIGHT   -> Triple(Color(0xFF000000), Color(0xFF001233),  null)
        SkyStyle.CLOUDY     -> Triple(Color(0xFF90A4AE), Color(0xFFCFD8DC),  null)
        SkyStyle.STORM      -> Triple(Color(0xFF263238), Color(0xFF455A64),  null)
        SkyStyle.FOG        -> Triple(Color(0xFFB0BEC5), Color(0xFFECEFF1),  null)
    }

    // Animate each stop independently — no loop, no rule violation
    val top by animateColorAsState(rawTop, tween(4000), label = "skyTop")
    val mid by animateColorAsState(rawMid, tween(4000), label = "skyMid")
    val bot by animateColorAsState(rawBot ?: rawMid, tween(4000), label = "skyBot")

    val gradient = if (rawBot != null) {
        Brush.verticalGradient(listOf(top, mid, bot))
    } else {
        Brush.verticalGradient(listOf(top, mid))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    )
}
