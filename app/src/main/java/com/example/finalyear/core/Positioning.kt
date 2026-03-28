package com.example.finalyear.core

import android.util.Log
import com.example.finalyear.coord.Hk1980
import com.example.finalyear.math.Const
import com.example.finalyear.math.MathFn
import com.example.finalyear.model.Ionospheric
import com.example.finalyear.model.Tropospheric
import com.example.finalyear.coord.Xyz
import org.ejml.simple.SimpleMatrix
import kotlin.math.abs
import kotlin.math.sqrt

object Positioning {
    fun findMatchingNavData(navDataList: List<NavData>,
                            obsData: ObsDataWithRange): NavData? {
        var bestNav: NavData? = null
        var bestTimeDiff = Double.POSITIVE_INFINITY
        for (navData in navDataList) {
            if (navData.prn == obsData.inner.prn) {
                val timeDiff = abs(obsData.inner.gpsTimeNs * 1e-9 - navData.gpsTimeSecs())
                if (timeDiff < bestTimeDiff) {
                    bestTimeDiff = timeDiff
                    bestNav = navData
                }
            }
        }
        return bestNav
    }

    // Least squares
    fun lsSingleEpoch(refNavDataList: List<NavData>,
                      refObsDataList: List<ObsDataWithRange>,
                      mode: Mode): Xyz {
        // Location of Sha Tin, Hong Kong, roughly the "centroid" of Hong Kong
        val approxPos = Xyz(-2414000.0, 5386000.0, 2417000.0)

        val navDataList = arrayListOf<NavData>()
        val obsDataList = arrayListOf<ObsDataWithRange>()
        for (obsData in refObsDataList) {
            val match = findMatchingNavData(refNavDataList, obsData) ?: continue
            val elevDeg = elevationDegOfSatellite(approxPos, navData = match, obsData = obsData)
            if (elevDeg < 15.0) {
                Log.d("GNSS", "Rejected with elevation: $elevDeg")
                continue
            }

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
            weightP[i, i] = 1 / (obsDataList[i].uncertainty * obsDataList[i].uncertainty)
        }

        val gpsPosList = SimpleMatrix(numberOfObs, 3)
        val satClockErrList = SimpleMatrix(numberOfObs, 1)

        // Least squares adjustments
        // Liu, Z.Z. (2025) Handout – V08 Satellite Positioning George Lec 5 2025 Feb. 1-41. [lecture notes, pdf] The Hong Kong Polytechnic University, unpublished.
        // Adapted from my own LSGI3322 Satellite Positioning final project
        var rxClockBiasM = 0.0  // Solves for the single unknown clock bias
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

            // Error function
            // Normal SPP:
            // P = rho + c(dT_usr - dt_sat) + I + T + ...
            // Residual = P - (rho + c(dT_usr - dt_sat) + I + T + ...)
            //          = P - rho + c·dt_sat - c·dT_usr - I - T - ...

            // DGNSS Type 1 RTCM:
            // PRC = P_base - rho_base
            //     ≈ c·dT_base + I_base + T_base + ...
            // Residual = (P_usr + PRC) - rho + c·dt_sat - c·dt_usr
            var f = (pseudorangeList.minus(rho)
                .plus(satClockErrList)  // c·dt
                .minus(rxClockBiasM))  // c·dT

            when (mode) {
                Mode.Spp -> {
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
                    f = f.minus(ionoDelays)  // I
                         .minus(tropoDelays)  // T
                    }
                Mode.Dgnss -> {}  // PRC contains dI, dT
            }

            // Weighted least squares adjustment
            // (B^T * P * B) ^ -1 * B^T * P * f
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

    enum class Mode {
        Spp,
        Dgnss;

        fun leastSquares(refNavDataList: List<NavData>,
                         refObsDataList: List<ObsDataWithRange>): Xyz {
            return lsSingleEpoch(
                refNavDataList = refNavDataList,
                refObsDataList = refObsDataList,
                mode = this,
            )
        }
    }

    fun twoSigmaRejectionAvg(posList: List<Hk1980.Grid>): Hk1980.Grid {
        require(posList.isNotEmpty())  // Outer function should handle emptiness, not here
        if (posList.size == 1) {
            return posList[0]
        }

        var rawMeanPos = Hk1980.Grid(0.0, 0.0, 0.0)
        for (pos in posList) {
            rawMeanPos.addAssign(pos)
        }
        rawMeanPos.divNum(posList.size.toDouble())

        val errSqList = posList.map {
            val dE = it.e - rawMeanPos.e
            val dN = it.n - rawMeanPos.n
            val dH = it.h - rawMeanPos.h
            dE * dE + dN * dN + dH * dH  // error^2 = sqrt(dE^2 + dN^2 + dH^2)^2, where error = delta distance
        }
        val sigmaDist = sqrt(errSqList.sum() / (posList.size - 1))
        val avgPos = Hk1980.Grid(0.0, 0.0, 0.0)
        var count = 0
        for (pos in posList) {
            val dE = pos.e - rawMeanPos.e
            val dN = pos.n - rawMeanPos.n
            val dH = pos.h - rawMeanPos.h
            val deltaDist = sqrt(dE * dE + dN * dN + dH * dH)

            if (deltaDist < 2 * sigmaDist) {
                avgPos.addAssign(pos)
                count++
            }
        }
        avgPos.divNum(count.toDouble())
        return avgPos
    }

    fun adjustAntennaHeight(pos: Hk1980.Grid) {
        pos.h -= Const.ANTENNA_HEIGHT_M
    }
}

// Rough estimation of elevation angle
private fun elevationDegOfSatellite(userPos: Xyz, navData: NavData, obsData: ObsDataWithRange): Double {
    val (satPos, _) = navData.calculateSatPosAndClock(
        obsData = obsData,
        approxPseudorange = obsData.pseudorange,
        rxClockErrSec = 0.0,
    )
    val elevationRad = userPos.toTopocentricAED(satPos).elevation
    return Math.toDegrees(elevationRad)
}