package com.example.services

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.util.Log

class MyraaVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceInteractionService"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "MyraaVoiceInteractionService is ready")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "MyraaVoiceInteractionService shutdown")
    }
}
