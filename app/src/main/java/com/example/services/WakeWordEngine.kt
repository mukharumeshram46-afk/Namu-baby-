package com.example.services

import android.util.Log

interface WakeWordEngine {
    fun initialize(): Boolean
    fun startListening(onWakeWordDetected: (String) -> Unit)
    fun stopListening()
    fun isAvailable(): Boolean
}

/**
 * NoOpWakeWordEngine:
 * Official placeholder architecture for hardware wake word ("Hey Piyush" / "Hey Myraa").
 * Avoids faking hotword detection while providing a strict pluggable interface for future OEM / on-device neural model integration.
 */
class NoOpWakeWordEngine : WakeWordEngine {
    companion object {
        private const val TAG = "NoOpWakeWordEngine"
    }

    override fun initialize(): Boolean {
        Log.i(TAG, "NoOpWakeWordEngine initialized (standby for native hotword model)")
        return true
    }

    override fun startListening(onWakeWordDetected: (String) -> Unit) {
        Log.i(TAG, "NoOpWakeWordEngine: tap-to-talk or foreground session active")
    }

    override fun stopListening() {
        Log.i(TAG, "NoOpWakeWordEngine: stopped listening")
    }

    override fun isAvailable(): Boolean = false
}
