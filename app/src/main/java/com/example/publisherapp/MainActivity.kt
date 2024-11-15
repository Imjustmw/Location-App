package com.example.publisherapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.publisherapp.Models.LocationData
import com.google.gson.Gson
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private var mqttClient: Mqtt5BlockingClient? = null
    private var isPublishing: Boolean = false

    // Track maximum and minimum speeds
    private var maxSpeed: Float = 0f
    private var minSpeed: Float = Float.MAX_VALUE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Grant Permissions
        checkLocationPermission()

        // Initialize LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize MQTT Client
        mqttClient = Mqtt5Client.builder()
            .identifier(UUID.randomUUID().toString())
            .serverHost("broker-816032311.sundaebytestt.com")
            .serverPort(1883)
            .build()
            .toBlocking()

        // Assign buttons here
        findViewById<Button>(R.id.btnStart).setOnClickListener {startPublishing()}
        findViewById<Button>(R.id.btnStop).setOnClickListener {stopPublishing()}
    }

    private fun startPublishing() {
        if (isPublishing) return
        if (findViewById<EditText>(R.id.etStudentId).text.toString().isEmpty()) {
            Toast.makeText(this, "Enter a student ID", Toast.LENGTH_SHORT).show()
            return
        }
        isPublishing = true

        // Connect to the MQTT broker
        try {
            mqttClient?.connect()
            Toast.makeText(this, "Connect to broker", Toast.LENGTH_SHORT).show()
        } catch(e: Exception) {
            Toast.makeText(this, "Failed to connect to broker", Toast.LENGTH_SHORT).show()
            isPublishing = true
            return
        }

        // Request location updates
        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val studentId = findViewById<EditText>(R.id.etStudentId).text.toString()

                // Update min and max speed
                val currentSpeed = location.speed
                if (currentSpeed > maxSpeed) maxSpeed = currentSpeed
                if (currentSpeed < minSpeed) minSpeed = currentSpeed

                // Create a LocationData object with max and min speeds
                val locationData = LocationData(
                    studentId = studentId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    maxSpeed = maxSpeed,
                    minSpeed = minSpeed
                )

                // Convert LocationData to JSON
                val gson = Gson()
                val message = gson.toJson(locationData)

                // Publish location as JSON
                publishLocation(message)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15000, 1f, locationListener)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun publishLocation(message: String) {
        try {
            mqttClient?.publishWith()
                ?.topic("assignment/location")
                ?.payload(message.toByteArray())
                ?.send()
            Log.d("MQTT", "Published: $message")
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to publish location data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPublishing() {
        if (!isPublishing) return
        isPublishing = false

        // Remove location updates
        try {
            locationManager.removeUpdates { }
            Toast.makeText(this, "Stopped location updates", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Failed to remove location updates", Toast.LENGTH_SHORT).show()
        }

        // Disconnect from the MQTT broker
        try {
            mqttClient?.disconnect()
            Toast.makeText(this, "Disconnected from broker", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to disconnect from broker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }
}