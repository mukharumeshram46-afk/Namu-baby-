package com.example.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MyraaGeofenceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Geofence transition broadcast received")
    }
}
