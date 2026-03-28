package com.example.finalyear.core

import com.example.finalyear.dgps.Rtcm

data class ObsDataWithRange(
    val inner: ObsData,
    var pseudorange: Double,
    val uncertainty: Double,
) {
    fun clone(): ObsDataWithRange {
        val obsData = inner.clone()
        return ObsDataWithRange(
            inner=obsData,
            pseudorange=pseudorange,
            uncertainty=uncertainty,
        )
    }

    fun adjustPseudorange(stationRtcmMap: Map<Int, Rtcm>): ObsDataWithRange? {
        val obsData = this.clone()

        val prn = obsData.inner.prn
        val time = obsData.inner.gpsTimeNs.toDouble() / 1e9  // Time of receiver receiving pseudorange

        var bestRtcm: Rtcm? = null
        var bestWeight = Double.NEGATIVE_INFINITY
        for (rtcm in stationRtcmMap.values) {
            val weight = rtcm.weightOfSat(prn, time) ?: continue
            if (weight > bestWeight) {  // w = 1/variance, the higher the better
                bestRtcm = rtcm
                bestWeight = weight
            }
        }

        // Skip if no PRN or not type 1
        val rtcm = bestRtcm ?: return null
        val dgps = rtcm.dgps[prn - 1] ?: return null
        val age = obsData.inner.gpsTimeNs * 1e-9 - rtcm.t0.secs

        val correctedPseudorange = obsData.pseudorange + dgps.prc + dgps.rrc * age
        obsData.pseudorange = correctedPseudorange

        return obsData
    }
}