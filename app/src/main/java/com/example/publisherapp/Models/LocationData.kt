package com.example.publisherapp.Models

data class LocationData(
    val studentId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val maxSpeed: Float,
    val minSpeed: Float
)
