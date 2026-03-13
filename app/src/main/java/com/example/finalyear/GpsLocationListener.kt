package com.example.finalyear

import android.location.Location
import android.location.LocationListener

class GpsLocationListener: LocationListener {
    var coarseLocation: DoubleArray? = null

    override fun onLocationChanged(location: Location) {
        coarseLocation = doubleArrayOf(
            location.latitude,
            location.longitude,
            location.altitude,
        )
    }
}