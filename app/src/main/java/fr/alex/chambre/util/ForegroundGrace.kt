package fr.alex.chambre.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

/** true while the app is on screen, and for [graceMs] after it leaves (a quick trip elsewhere keeps the connection). */
@OptIn(ExperimentalCoroutinesApi::class)
fun activeWithGrace(foreground: Flow<Boolean>, graceMs: Long): Flow<Boolean> =
    foreground.transformLatest { shown ->
        if (!shown) delay(graceMs)
        emit(shown)
    }.distinctUntilChanged()
