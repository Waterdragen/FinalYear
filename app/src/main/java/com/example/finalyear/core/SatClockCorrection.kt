package com.example.finalyear.core

import android.util.Log
import com.example.finalyear.Const
import com.example.finalyear.MathFn
import kotlin.math.abs
import kotlin.math.sin

data class SatClockCorrection(
    val satClockCorrectionMeters: Double,
    val eccAnomalyRadians: Double,
    val timeFromRefEpochSec: Double,
) {
    companion object {
        fun fromNavAndGpsTime(navData: NavData, arrivalTow: Double, weekNum: Double): SatClockCorrection {
            val n = navData.meanMotion()
            val timeOfTransmissionIncludingRxWeekSec = weekNum * 604800.0 + arrivalTow
            var tcSec = timeOfTransmissionIncludingRxWeekSec - (navData.weekNo * 604800 + navData.t0)
            tcSec = MathFn.fixWeekRollover(tcSec)

            var changeInSatClockCorr = Double.POSITIVE_INFINITY
            val initSatClockCorrSec = navData.satelliteClockErrorSec(tcSec)
            var satClockCorrSec = initSatClockCorrSec
            var e = 0.0
            var corrCounter = 0
            val tolerance = 1e-11
            val maxIterations = 100

            while (changeInSatClockCorr > tolerance) {
                var tkSec = timeOfTransmissionIncludingRxWeekSec - (
                        navData.weekNo * 604800 + navData.t0 + satClockCorrSec
                        )
                tkSec = MathFn.fixWeekRollover(tkSec)
                val m = navData.meanAnomaly(n, tkSec)
                e = navData.eccAnomaly(m)
                val relativisticCorr = Const.RelativisticF * navData.ecc * navData.sqrtA * sin(e)
                val newSatClockCorrSec  = initSatClockCorrSec + relativisticCorr
                changeInSatClockCorr = abs(satClockCorrSec - newSatClockCorrSec)
                satClockCorrSec = newSatClockCorrSec
                corrCounter += 1

                if (corrCounter > maxIterations) {
                    throw RuntimeException("Sat clock correction did not converge, the error is $changeInSatClockCorr")
                }
            }

            var tkSec = timeOfTransmissionIncludingRxWeekSec - (
                    navData.weekNo * 604800 + navData.t0 + satClockCorrSec
                    )
            tkSec = MathFn.fixWeekRollover(tkSec)

            return SatClockCorrection(satClockCorrSec * Const.c, e, tkSec)
        }
    }
}