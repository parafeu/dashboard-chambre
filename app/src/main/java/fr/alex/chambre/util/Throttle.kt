package fr.alex.chambre.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Limits how often [send] is called: the first value goes out immediately, values submitted
 * during the following [intervalMs] are coalesced and only the latest is sent when it ends.
 */
class Throttle<T>(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 150,
    private val send: suspend (T) -> Unit,
) {
    private var job: Job? = null
    private var pending: Any? = NONE

    fun submit(value: T) {
        if (job?.isActive == true) {
            pending = value
        } else {
            start(value)
        }
    }

    /** Sends [value] right away, dropping any pending value. */
    fun flush(value: T) {
        job?.cancel()
        start(value)
    }

    private fun start(value: T) {
        pending = NONE
        job = scope.launch {
            send(value)
            while (true) {
                delay(intervalMs)
                val next = pending
                if (next === NONE) break
                pending = NONE
                @Suppress("UNCHECKED_CAST")
                send(next as T)
            }
        }
    }

    private companion object {
        val NONE = Any()
    }
}
