package com.example.finalyear.core

import android.util.Log
import com.example.finalyear.math.Const
import com.example.finalyear.math.MathFn
import com.example.finalyear.model.Ionospheric
import com.example.finalyear.model.Tropospheric
import com.example.finalyear.coord.Xyz
import org.ejml.simple.SimpleMatrix
import kotlin.math.abs

object Positioning {
    fun findMatchingNavData(navDataList: List<NavData>,
                            obsData: ObsDataWithRange): NavData? {
        var bestNav: NavData? = null
        var bestTimeDiff = Double.POSITIVE_INFINITY
        for (navData in navDataList) {
            if (navData.prn == obsData.inner.prn) {
                val timeDiff = abs(obsData.inner.gpsTimeNs * 1e-9
                                   - MathFn.datetimeToGpsTime(navData.dateTime))
                if (timeDiff < bestTimeDiff) {
                    bestTimeDiff = timeDiff
                    bestNav = navData
                }
            }
        }
        return bestNav
    }

    fun sppSingleEpoch(refNavDataList: List<NavData>,
                       refObsDataList: List<ObsDataWithRange>): Xyz {
        val navDataList = arrayListOf<NavData>()
        val obsDataList = arrayListOf<ObsDataWithRange>()
        for (obsData in refObsDataList) {
            val match = findMatchingNavData(refNavDataList, obsData) ?: continue
            navDataList.add(match)
            obsDataList.add(obsData)
        }

        val numberOfObs = obsDataList.size
        if (numberOfObs < 4) {
            throw MyException.NotEnoughSatellites("Expected 4 satellites, got $numberOfObs")
        }

        val pseudorangeList = SimpleMatrix(numberOfObs, 1)
        val weightP = SimpleMatrix(numberOfObs, numberOfObs)

        for (i in 0 until numberOfObs) {
            pseudorangeList[i] = obsDataList[i].pseudorange
            weightP[i, i] = obsDataList[i].inner.signalToNoiseRatioDb + 1
        }

        val gpsPosList = SimpleMatrix(numberOfObs, 3)
        val satClockErrList = SimpleMatrix(numberOfObs, 1)

        // Least squares adjustments
        val approxPos = Xyz(-2414000.0, 5386000.0, 2417000.0)  // Location of Sha Tin, Hong Kong, roughly the "centroid" of Hong Kong

        var rxClockBiasM = 0.0
        var adjustments = SimpleMatrix(4, 1)
        adjustments.fill(Double.POSITIVE_INFINITY)
        var iterations = 0

        while (abs(adjustments[0]) + abs(adjustments[1])
               + abs(adjustments[2]) + abs(adjustments[3]) > 1e-6) {
            val rxClockErrSec = rxClockBiasM / Const.c

            // Replace empty or previous satellite positions
            for (i in 0 until numberOfObs) {
                var pos = Xyz(0.0, 0.0, 0.0)
                var satClockErrM = 0.0
                var expectedPseudorange = 70e-3 * Const.c
                repeat(10) {
                    val (newPos, newSatClockErrM) = navDataList[i].calculateSatPosAndClock(
                        obsData = obsDataList[i],
                        approxPseudorange = expectedPseudorange,
                        rxClockErrSec = rxClockErrSec,
                    )
                    pos = newPos
                    satClockErrM = newSatClockErrM
                    expectedPseudorange = MathFn.expectedPseudorange(approxPos, pos)
                }

                gpsPosList[i, 0] = pos.x
                gpsPosList[i, 1] = pos.y
                gpsPosList[i, 2] = pos.z
                satClockErrList[i] = satClockErrM
            }

            // Expected geometric distance to the satellites
            val rho = MathFn.expectedPseudoranges(approxPos, gpsPosList)

            // Design matrix
            val b = MathFn.designMatrix(approxPos, gpsPosList, rho)
            val bT = b.transpose()

            // Ionospheric delay
            val ionoDelays = Ionospheric.delays(
                navDataList = navDataList,
                obsDataList = obsDataList,
                userPos = approxPos,
                satPosList = gpsPosList,
                frequencyHz = Const.L1_FREQ_HZ,
            )

            // Tropospheric delay
            val tropoDelays = Tropospheric.delays(
                userPos = approxPos,
                satPosList = gpsPosList,
            )

            // Error function
            // dP = P - (rho + c(dT_usr - dt_sat) + I + T + ...)
            //    = P - rho + c * dt_sat - c * dT_usr - I - T - ...
            val f = (pseudorangeList.minus(rho)
                .plus(satClockErrList)  // c * dt
                .minus(rxClockBiasM))  // c * dT
                .minus(ionoDelays)  // I
                .minus(tropoDelays)  // T

            // Least squares adjustment
            // (B^T * B) ^ -1 * B^T * f
            adjustments = bT.mult(weightP).mult(b).invert().mult(bT).mult(weightP).mult(f)

            approxPos.x += adjustments[0]
            approxPos.y += adjustments[1]
            approxPos.z += adjustments[2]
            rxClockBiasM += adjustments[3]

            iterations++
            if (iterations > 100) {
                Log.w("GNSS", "adjustments: $adjustments")
                Log.w("GNSS", "approxPos: $approxPos")
                throw MyException.LsConvergeFail("Least squares failed to converge after 100 iterations")
            }
        }

        return approxPos
    }
}