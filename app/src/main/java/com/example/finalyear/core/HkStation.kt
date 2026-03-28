package com.example.finalyear.core

import android.util.Log
import kotlin.math.abs
import kotlin.math.max

data class HkStation(
    val stnNum: String,
    val locality: String,
    val easting: Double,
    val northing: Double,
    val height: Double,
) {
    companion object {
        // 1. Sort by easting
        // 2. Find the quartile (easting) to start linear search
        // 3. Store all stations dE and dN within 3km
        // 4. Sort by euclidean distance, return at most 5 candidates
        fun findNearest(hkStationList: List<HkStation>, easting: Double, northing: Double): List<HkStation> {
            val size = hkStationList.size
            // 1/4, 1/2, 3/4 of the list
            val deltaEList = doubleArrayOf(
                easting - hkStationList[size shr 2].easting,
                easting - hkStationList[size shr 1].easting,
                easting - hkStationList[(size * 3) shr 2].easting
            )
            val startingPoint = if (deltaEList[2] > 3000.0)  {
                (size * 3) shr 2
            } else if (deltaEList[1] > 3000.0) {
                size shr 1
            } else if (deltaEList[0] > 3000.0) {
                size shr 2
            } else {
                0
            }
            val filteredHkStationList = arrayListOf<HkStation>()
            for (i in startingPoint until size) {
                val hkStation = hkStationList[i]
                // Check 3 km x 3 km
                val dE = easting - hkStation.easting
                val dN = northing - hkStation.northing
                if (abs(dE) < 3000.0 && abs(dN) < 3000.0) {
                    filteredHkStationList.add(hkStation)
                }
                if (dE < -3000.0) {  // We have looped past all reasonable results
                    break
                }
            }
            filteredHkStationList.sortBy {
                val dE = easting - it.easting
                val dN = northing - it.northing
                dE * dE + dN * dN
            }
            // take at most 5
            val filteredSize = filteredHkStationList.size
            repeat(max(filteredSize - 5, 0)) {
                filteredHkStationList.removeLast()
            }
            return filteredHkStationList
        }
    }
}
