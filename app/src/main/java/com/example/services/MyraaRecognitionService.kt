package com.example.services

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

class MyraaRecognitionService : RecognitionService() {

    companion object {
        private const val TAG = "MyraaRecognitionService"
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        Log.i(TAG, "Recognition service started listening")
    }

    override fun onCancel(listener: Callback?) {
        Log.i(TAG, "Recognition service cancelled")
    }

    override fun onStopListening(listener: Callback?) {
        Log.i(TAG, "Recognition service stopped listening")
    }
}
