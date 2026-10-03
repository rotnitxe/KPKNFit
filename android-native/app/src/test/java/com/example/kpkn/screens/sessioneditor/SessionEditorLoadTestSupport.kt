package com.example.kpkn.screens.sessioneditor

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Waits until the editor's own load has published its session.
 *
 * It joins the load that is really in flight and only restarts one when the previous load
 * ended with nothing on screen (a failure, or a repository that was not ready yet). Polling
 * `uiState.session` and calling `retryLoadSession()` on every pass instead adds a second,
 * concurrent load: under the unconfined test Main dispatcher it publishes on an IO thread and
 * can land after the test already switched variant, edited rule defaults or added an exercise.
 *
 * Once this returns no load is in flight, and the ViewModel discards any later one
 * (a loaded editor is never replaced; see `SessionEditorViewModel.publishLoadedState`).
 */
internal suspend fun SessionEditorViewModel.awaitSessionLoaded(timeoutMillis: Long = 5_000) {
    withTimeout(timeoutMillis) {
        while (true) {
            awaitSessionLoadSettled()
            if (uiState.value.session != null) return@withTimeout
            retryLoadSession()
            delay(50)
        }
    }
}
