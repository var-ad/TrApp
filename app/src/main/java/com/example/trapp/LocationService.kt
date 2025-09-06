package com.example.trapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")

        startForegroundServiceNotification()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startLocationUpdates()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TrAPP")
            .setContentText("Logging your location...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 120000L)
            .setMinUpdateIntervalMillis(100000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                Log.d("LocationService", "Location: ${it.latitude}, ${it.longitude}")
                sendLocationToServer(it.latitude, it.longitude)
            }
        }
    }

    private fun sendLocationToServer(lat: Double, lon: Double) {
        val json = """{"latitude": $lat, "longitude": $lon}"""
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            json
        )

        val request = Request.Builder()
            .url("https://trapp-backend.onrender.com/location")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LocationService", "Failed to send location", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                Log.d("LocationService", "Location sent successfully")
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Service destroyed")
    }

    // 🔑 THIS MAKES IT RESTART AFTER BEING KILLED
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "onStartCommand called")
        return START_STICKY
    }
}
