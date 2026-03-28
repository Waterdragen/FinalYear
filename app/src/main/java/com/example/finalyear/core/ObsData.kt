package com.example.finalyear.core

import com.example.finalyear.math.Const
import com.example.finalyear.math.MathFn
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

data class ObsData (
    val prn: Int,

    // Raw clock values for debugging purposes
    // Nanos since 1980-1-6
    val gpsTimeNs: Long,

    // Receiver side
    val rxTimeNs: Long,

    // Transmitter side
    val txTimeNs: Long,
    val txTimeOffsetNs: Long,

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
            "rxTimeNs",
            "txTimeNs",
            "txTimeOffsetNs",

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
            prn, gpsTimeNs, rxTimeNs, txTimeNs, txTimeOffsetNs, pseudorangeRateMps, pseudorangeRateUncertaintyMps, adrMeters, adrMetersValid, adrUncertaintyMeters, signalToNoiseRatioDb
        )
    }

    fun computePseudorange(): ObsDataWithRange {
        val txTimeNsCorr = txTimeNs + txTimeOffsetNs
        val deltaSec = MathFn.fixWeekRollover((rxTimeNs - txTimeNsCorr) * 1e-9)
        val pseudorange = deltaSec * Const.c

        // Van Dierendonck A. J. (1996) "GPS receivers". Global Positioning System: theory and applications. Cap. 8. Vol. 1, AIAA Progress in Astronautics and Aeronautics.
        // sigma = sqrt(
        //         B_L * d / (2 * SNR)
        //     * (1 + 2 / ((2-d) * SNR * T)
        // )
        // T: predetection integration time
        // B_L: one sided code tracking loop bandwidth
        // d: correlator chip spacing
        val snrLinear = 10.0.pow(signalToNoiseRatioDb / 10.0)
        val d = Const.GPS_CORRELATOR_SPACING_IN_CHIPS
        val t = Const.GPS_DLL_AVERAGING_TIME_SEC
        val bL = Const.GPS_BANDWIDTH
        val sigmaSec = sqrt(
            bL * d / (2 * snrLinear)
            * (1 + 2 / ((2-d) * snrLinear * t))
        )
        val sigmaM = sigmaSec * Const.c
        return ObsDataWithRange(
            inner = this,
            pseudorange = pseudorange,
            uncertainty = sigmaM,
        )
    }
}