package com.example.maprouter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        // Get the transition type (e.g., ENTER, EXIT, DWELL)
        val geofenceTransition = geofencingEvent.geofenceTransition

        // only process geo fence ENTER
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            //process arrival time
            val arrivalTime = System.currentTimeMillis()
            val sharedPrefs = context.getSharedPreferences("TripPrefs", Context.MODE_PRIVATE)
            val startTime = sharedPrefs.getLong("start_time", 0L)
            val totalTimeMillis = arrivalTime - startTime
            val totalSeconds = totalTimeMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

            //remove geo fencing
            val geofencingClient = LocationServices.getGeofencingClient(context)
            geofencingClient.removeGeofences(listOf("destination_geofence"))
                .addOnSuccessListener {
                    Log.d("TripReceiver", "Geofence successfully removed.")
                }
                .addOnFailureListener { e ->
                    Log.e("TripReceiver", "Failed to remove geofence: ${e.message}")
                }
        }
    }
}

