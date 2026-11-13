package com.example.maprouter

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

data

class GeofenceManager(private val context: Context) {
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    fun registerDestinationGeofence(destLat: Double, destLng: Double) {
        // Save current start time in SharedPreferences
        val sharedPrefs = context.getSharedPreferences("TripPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putLong("start_time", System.currentTimeMillis()).apply()

        // Define the geofence parameters (e.g., 100 meters radius around destination)
        val geofence = Geofence.Builder()
            .setRequestId("destination_geofence")
            .setCircularRegion(destLat, destLng, 50f) // 10 meters radius
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val geofencingRequest = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofence(geofence)
        }.build()

        // Create a PendingIntent that triggers our BroadcastReceiver
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Register the geofence (requires ACCESS_FINE_LOCATION permission granted)
        try {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent).run {
                addOnSuccessListener {

                }
                addOnFailureListener { e ->

                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}