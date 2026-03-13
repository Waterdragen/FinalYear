package com.example.finalyear.core

import java.io.File

data class ObsData (
    val prn: Int,

    // Raw clock values for debugging purposes
    // Nanos since 1980-1-6
    val gpsTimeNs: Long,
//    val arrivalTowNs: Long,  // old
//    val recvSvTowNs: Long,  // old
    val fullBiasNs: Long,  // new
    val biasNs: Long,  // new


    // Receiver side
    val rxTimeNs: Long,  // new

    // Transmitter side
    val txTimeNs: Long,  // new
    val txTimeOffsetNs: Long,  // new

    val pseudorangeRateMps: Double,
    val pseudorangeRateUncertaintyMps: Double,

    // Accumulated delta range (meters)
    val adrMeters: Double,
    val adrMetersValid: Boolean,
    val adrUncertaintyMeters: Double,
    val signalToNoiseRatioDb: Double,
) {
    companion object {
        val FIELDS = arrayOf(
            "prn",
            "gpsTimeNs",
//            "arrivalTimeNs",  // old
//            "recvSvTimeNs",  // old
            "fullBiasNs",  // new
            "biasNs",  // new
            "rxTimeNs",  // new
            "txTimeNs",  // new
            "txTimeOffsetNs",  // new

            "pseudorangeRateMps",
            "pseudorangeRateUncertaintyMps",
            "adrMeters",
            "adrMetersValid",
            "adrUncertaintyMeters",
            "signalToNoiseRatioDb"
        )

        fun writeCsvHeader(file: File) {
            val sb = StringBuilder()
            for (field in FIELDS) {
                sb.append(field)
                sb.append(',')
            }
            sb.append('\n')
            file.writeText(sb.toString())
        }
    }

    fun writeCsvRow(file: File) {
        val sb = StringBuilder()
        sb.append(prn.toString()); sb.append(',')
        sb.append(gpsTimeNs.toString()); sb.append(',')
//        sb.append(arrivalTowNs.toString()); sb.append(',')  // old
//        sb.append(recvSvTowNs.toString()); sb.append(',')  // old
        sb.append(fullBiasNs.toString()); sb.append(',')
        sb.append(biasNs.toString()); sb.append(',')
        sb.append(rxTimeNs.toString()); sb.append(',')
        sb.append(txTimeNs.toString()); sb.append(',')
        sb.append(txTimeOffsetNs.toString()); sb.append(',')

        sb.append(pseudorangeRateMps.toString()); sb.append(',')
        sb.append(pseudorangeRateUncertaintyMps.toString()); sb.append(',')
        sb.append(adrMeters.toString()); sb.append(',')
        sb.append(adrMetersValid.toString()); sb.append(',')
        sb.append(adrUncertaintyMeters.toString()); sb.append(',')
        sb.append(signalToNoiseRatioDb.toString()); sb.append(',')
        sb.append('\n')

        file.appendText(sb.toString())
    }

    fun clone(): ObsData {
        return ObsData(
            prn, gpsTimeNs, fullBiasNs, biasNs, rxTimeNs, txTimeNs, txTimeOffsetNs, pseudorangeRateMps, pseudorangeRateUncertaintyMps, adrMeters, adrMetersValid, adrUncertaintyMeters, signalToNoiseRatioDb
        )
    }
}