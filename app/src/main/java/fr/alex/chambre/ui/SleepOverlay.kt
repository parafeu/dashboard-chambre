package fr.alex.chambre.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Black veil shown while the screen sleeps (touches are filtered upstream by TouchGate). */
@Composable
fun SleepOverlay(asleep: Boolean) {
    AnimatedVisibility(asleep, enter = fadeIn(tween(600)), exit = fadeOut(tween(250))) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }
}
