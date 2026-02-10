package com.psadiag.protocol

import android.util.Log
import com.psadiag.bluetooth.ELM327Manager
import kotlinx.coroutines.*

/**
 * Maintains the UDS extended diagnostic session by periodically sending TesterPresent (3E00).
 * Without this, the ECU reverts to default session after ~5 seconds.
 */
class TesterPresentManager(private val elm: ELM327Manager) {

    companion object {
        private const val TAG = "TesterPresent"
        private const val INTERVAL_MS = 2000L  // Send every 2 seconds
    }

    private var job: Job? = null
    private var isRunning = false

    /**
     * Start sending TesterPresent periodically.
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true

        job = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "TesterPresent started (interval: ${INTERVAL_MS}ms)")
            while (isActive && isRunning) {
                try {
                    val response = elm.sendRawSimple(PSAProtocol.UDS.TESTER_PRESENT)
                    if (response.contains("NO DATA") || response.contains("ERROR")) {
                        Log.w(TAG, "TesterPresent failed: $response")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TesterPresent error: ${e.message}")
                }
                delay(INTERVAL_MS)
            }
        }
    }

    /**
     * Stop sending TesterPresent.
     */
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        Log.d(TAG, "TesterPresent stopped")
    }
}
