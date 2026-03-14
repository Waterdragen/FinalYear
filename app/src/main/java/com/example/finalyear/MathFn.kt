package com.example.finalyear

import android.util.Log
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsDataWithRange
import com.example.finalyear.core.PositionAndRangeResidual
import com.example.finalyear.core.PositionAndVelocity
import com.example.finalyear.core.SatClockCorrection
import com.example.finalyear.model.Tropospheric
import com.example.finalyear.util.Point
import com.example.finalyear.util.Xyz
import org.ejml.simple.SimpleMatrix
import org.joda.time.DateTime
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.math.*

object MathFn {
    private val ORIGIN = LocalDate.of(1, 1, 1)

    fun dateNum(dt: DateTime): Int {
        val javaDate = dt.toGregorianCalendar().toZonedDateTime().toLocalDate()
        val ordinal = ChronoUnit.DAYS.between(ORIGIN, javaDate) + 1
        return ordinal.toInt() + 366
    }

    fun timeNum(dt: DateTime): Int {
        val weekdayNum = (dt.toLocalDate().dayOfWeek % 7).toLong()
        val duration = Duration.ofDays(weekdayNum)
            .plusHours(dt.hourOfDay.toLong())
            .plusMinutes(dt.minuteOfDay.toLong())
            .plusSeconds(dt.secondOfDay.toLong())
        return duration.seconds.toInt()
    }

    fun timeDiffK(t: Double, t0: Double): Double {
        var tk = t - t0
        if (tk > Const.HALF_WEEK) {
            tk -= 2 * Const.HALF_WEEK
        } else if (tk < -Const.HALF_WEEK) {
            tk += 2 * Const.HALF_WEEK
        }
        return tk
    }

    fun fixWeekRollover(t: Double): Double {
        var tCorrected = t
        if (tCorrected > Const.HALF_WEEK) {
            tCorrected -= 2 * Const.HALF_WEEK
        } else if (tCorrected < -Const.HALF_WEEK) {
            tCorrected += 2 * Const.HALF_WEEK
        }
        return tCorrected
    }

    fun gpsPositionEcef(x0: Double, y0: Double, omegaK: Double, i: Double): Xyz {
        val x = x0 * cos(omegaK) - y0 * cos(i) * sin(omegaK)
        val y = x0 * sin(omegaK) + y0 * cos(i) * cos(omegaK)
        val z = y0 * sin(i)
        return Xyz(x, y, z)
    }

    fun correctEarthRotation(pos: Xyz, timeTransmission: Double): Xyz {
        // Rotated angle ωτ (rad) = rotation speed Ω_e (rad/s) * time (s)
        val rotatedAngle = Const.OmegaE * timeTransmission
        val newX = pos.x * cos(rotatedAngle) + pos.y * sin(rotatedAngle)
        val newY = pos.x * -sin(rotatedAngle) + pos.y * cos(rotatedAngle)
        return Xyz(newX, newY, pos.z)
    }

    fun gpsPositionInOrbitalPlane(r: Double, phiC: Double): Point {
        val x0 = r * cos(phiC)
        val y0 = r * sin(phiC)
        return Point(x0, y0)
    }

    fun calculateCorrectedTransmitTowAndWeek(navData: NavData, arrivalTowSec: Double, weekNum: Double, pseudorange: Double): Pair<Double, Double> {
        var weekNum = weekNum
        var rxTowAtTimeOfTransmission = arrivalTowSec - pseudorange / Const.c
        if (rxTowAtTimeOfTransmission < 0) {
            rxTowAtTimeOfTransmission += 604800
            weekNum -= 1
        } else if (rxTowAtTimeOfTransmission > 604800) {
            rxTowAtTimeOfTransmission -= 604800
            weekNum += 1
        }

        val satClockCorr = SatClockCorrection.fromNavAndGpsTime(navData, rxTowAtTimeOfTransmission, weekNum)
        val clockCorrSec = satClockCorr.satClockCorrectionMeters / Const.c

        var rxTowAtTimeOfTransmissionCorrected = rxTowAtTimeOfTransmission + clockCorrSec
        if (rxTowAtTimeOfTransmissionCorrected < 0) {
            rxTowAtTimeOfTransmissionCorrected += 604800
            weekNum -= 1
        } else if (rxTowAtTimeOfTransmissionCorrected > 604800) {
            rxTowAtTimeOfTransmissionCorrected -= 604800
            weekNum += 1
        }

        return Pair(rxTowAtTimeOfTransmissionCorrected, weekNum)
    }

    fun calculateSatellitePosAndVel(
        navData: NavData,
        towCorrected: Double,
        weekNum: Double,
        range: Double,
        rangeRate: Double,
    ): PositionAndVelocity {
        val satClockCorr = SatClockCorrection.fromNavAndGpsTime(navData, towCorrected, weekNum)
        val e = satClockCorr.eccAnomalyRadians
        val tkSec = satClockCorr.timeFromRefEpochSec

        val theta = navData.trueAnomaly(e)
        var phi = navData.argumentOfLatitude(theta)
        var r = navData.orbitRadius(e)
        var i0 = navData.i0

        r += navData.radiusCorrection(phi)
        phi += navData.argumentOfLatitudeCorrection(phi)
        i0 += navData.inclinationCorrection(phi)

        val (x, y) = gpsPositionInOrbitalPlane(r, phi)
        val omegaK = navData.longitudeOfAscendingNode(tkSec, range)
        val satPos = gpsPositionEcef(x, y, omegaK, i0)

        val n = navData.meanMotion()

        // Derivative of mean anomaly
        val nVel = n
        // Derivative of eccentric anomaly
        val eVel = nVel / (1.0 - navData.ecc * cos(e))
        // Derivative of true anomaly
        val thetaVel = sin(e) * eVel * (1.0 + navData.ecc * cos(theta)) / (
                sin(theta) * (1.0 - navData.ecc * cos(e))
                )
        // Derivative of argument of latitude
        val phiVel = thetaVel + 2.0 * (
                navData.cus * cos(2.0 * phi) - navData.cuc * sin(2.0 * phi)
                ) * thetaVel
        // Derivative of radius of satellite orbit
        val rVel = (
                navData.sqrtA * navData.sqrtA
                        * navData.ecc * sin(e) * n
                        / (1.0 - navData.ecc * cos(e))
                        + 2.0 * (navData.crs * cos(2.0 * phi) - navData.crc * sin(2.0 * phi)) * thetaVel
                )
        // Derivative of inclination
        val iVel = navData.dI + (
                navData.cis * cos(2.0 * phi) - navData.cic * sin(2.0 * phi)
                ) * 2.0 * thetaVel

        val xVel = rVel * cos(phi) - y * phiVel
        val yVel = rVel * sin(phi) + x * phiVel

        // Corrected rate of right ascension for the Sagnac effect
        val dOmegaVel = navData.dOmega - Const.OmegaE * (
                1.0 + rangeRate / Const.c
                )

        val satXVel = (xVel - y * cos(i0) * dOmegaVel) * cos(omegaK) - (
                    x * dOmegaVel
                    + yVel * cos(i0)
                    - y * sin(i0) * iVel
                ) * sin(omegaK)
        val satYVel = (xVel - y * cos(i0) * dOmegaVel) * sin(omegaK) + (
                    x * dOmegaVel
                    + yVel * cos(i0)
                    - y * sin(i0) * iVel
                ) * cos(omegaK)
        val satZVel = yVel * sin(i0) + y * cos(i0) * iVel

        return PositionAndVelocity(
            satPos.x,
            satPos.y,
            satPos.z,
            satXVel,
            satYVel,
            satZVel,
        )
    }

    fun computeUserToSatelliteRangeAndRangeRate(
        userPosAndVel: PositionAndVelocity,
        satPosAndVel: PositionAndVelocity,
    ): Pair<Double, Double> {
        val dx = satPosAndVel.x - userPosAndVel.x
        val dy = satPosAndVel.y - userPosAndVel.y
        val dz = satPosAndVel.z - userPosAndVel.z

        val newRange = sqrt(dx * dx + dy * dy + dz * dz)
        val newRangeRate = (
                    (userPosAndVel.velX - satPosAndVel.velX) * dx
                    + (userPosAndVel.velY - satPosAndVel.velY) * dy
                    + (userPosAndVel.velZ - satPosAndVel.velZ) * dz
                ) / newRange

        return Pair(newRange, newRangeRate)
    }

    fun calculateSatPosAndVel(
        navData: NavData,
        arrivalTowSec: Double,
        weekNum: Double,
        posEcef: SimpleMatrix,
    ): PositionAndVelocity {
        var tempRange = 0.07 * Const.c  // initial guess: 70 ms and zero velocity
        var tempRangeRate = 0.0

        var satPosAndVel = PositionAndVelocity(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val userPosAndVel = PositionAndVelocity(posEcef[0], posEcef[1], posEcef[2], 0.0, 0.0, 0.0)

        // We use 5 iterations for now, satPosAndVel is replaced
        repeat(5) {
            satPosAndVel = calculateSatellitePosAndVel(
                navData = navData,
                towCorrected = arrivalTowSec,
                weekNum = weekNum,
                range = tempRange,
                rangeRate = tempRangeRate,
            )
            val (newRange, newRangeRate) = computeUserToSatelliteRangeAndRangeRate(
                userPosAndVel = userPosAndVel,
                satPosAndVel = satPosAndVel
            )
            tempRange = newRange
            tempRangeRate = newRangeRate
        }

        return satPosAndVel
    }

    fun calculateSatPosAndPseudorangeResidual(
        navDataList: List<NavData>,
        obsDataList: List<ObsDataWithRange>,
        posEcef: SimpleMatrix,
        arrivalTowSec: Double,
        weekNum: Double,
    ): PositionAndRangeResidual {
        var arrivalTowSec = arrivalTowSec
        val correctedArrivalTowSec = arrivalTowSec - posEcef[3] / Const.c  // new
        var weekNum = weekNum

        val numSats = navDataList.size
        val deltaRangeMeters = SimpleMatrix(numSats, 1)
        val satPosEcefMeters = SimpleMatrix(numSats, 3)
        val satPrns = ArrayList(Collections.nCopies(numSats, -1))
        val covarMatrixMetersSq = SimpleMatrix(numSats, numSats)
        val ionoCorrection = navDataList[0].ionoCorrection

        val userPosTempEcefMeters = doubleArrayOf(posEcef[0], posEcef[1], posEcef[2])
        var satCounter = 0

        for (i in 0 until numSats) {
            val navData = navDataList[i]
            val obsData = obsDataList[i]
//            arrivalTowSec -= posEcef[3] / Const.c  // old

            val pseudorange = obsData.pseudorange
            val pseudorangeUncertainty = obsData.uncertainty

            // Only set the diagonal (uncorrelated pseudorange measurements
            covarMatrixMetersSq[satCounter, satCounter] = pseudorangeUncertainty * pseudorangeUncertainty

            // calculate corrected transmit tow and week
            // adjust for week rollover
            val (rxTowAtTimeOfTransmissionCorrected, newWeekNum) = calculateCorrectedTransmitTowAndWeek(
                navData = navData,
//                arrivalTowSec = arrivalTowSec,  // old
                arrivalTowSec = correctedArrivalTowSec,  // new
                weekNum = weekNum,
                pseudorange = pseudorange,
            )
            weekNum = newWeekNum

            // calculate satellite position and velocity from ephemeris
            val satPosAndVel = calculateSatPosAndVel(
                navData = navData,
                arrivalTowSec = rxTowAtTimeOfTransmissionCorrected,
                weekNum = weekNum,
                posEcef = posEcef,
            )

            satPosEcefMeters[satCounter, 0] = satPosAndVel.x
            satPosEcefMeters[satCounter, 1] = satPosAndVel.y
            satPosEcefMeters[satCounter, 2] = satPosAndVel.z

            // Calculate ionospheric and tropospheric corrections
            val ionosphericCorr = ionoCorrection.klobucharCorrectionSec(
                userPosEcefMeters = userPosTempEcefMeters,
                satPosEcefMeters = satPosEcefMeters.extractVector(true, satCounter),
                gpsTowSec = rxTowAtTimeOfTransmissionCorrected,
                frequencyHz = 1.57542e9  // L1 Frequency
            ) * Const.c

            val troposphericCorr = Tropospheric.calculateTroposphericDelayMeters(
                userPosEcefMeters = userPosTempEcefMeters,
                satPosEcefMeters = satPosEcefMeters,
            )

            // Calculate predicted pseudorange
            // - calculate the satellite clock drift
            val satClockCorr = SatClockCorrection.fromNavAndGpsTime(navData, rxTowAtTimeOfTransmissionCorrected, weekNum)

            val satClockCorrMeters = satClockCorr.satClockCorrectionMeters
            val dPos = doubleArrayOf(
                satPosEcefMeters[satCounter, 0] - userPosTempEcefMeters[0],
                satPosEcefMeters[satCounter, 1] - userPosTempEcefMeters[1],
                satPosEcefMeters[satCounter, 2] - userPosTempEcefMeters[2],
            )
            val satToUserDistance = sqrt(dPos[0] * dPos[0] + dPos[1] * dPos[1] + dPos[2] * dPos[2])
            val predictedPseudorange = (
                    satToUserDistance
                    - satClockCorrMeters
                    + ionosphericCorr
                    + troposphericCorr
                    + posEcef[3]
            )
            Log.d("GNSS", "posEcef[3]: ${posEcef[3]}")

            // pseudorange residual (difference of measured to predicted)
            deltaRangeMeters[satCounter] = pseudorange - predictedPseudorange
            Log.d("GNSS", "Predicted: $predictedPseudorange")
            Log.d("GNSS", "Pseudorange: $pseudorange")

            // Satellite PRNs
            satPrns[satCounter] = obsData.inner.prn
            satCounter += 1
        }

        return PositionAndRangeResidual(
            satPrns = satPrns,
            satPosMeters = satPosEcefMeters,
            deltaRangeMeters = deltaRangeMeters,
            covarMatrixMetersSq = covarMatrixMetersSq,
        )
    }

    fun calculateGeometryMatrix(
            satPosEcefMeters: SimpleMatrix,
            posEcef: SimpleMatrix): SimpleMatrix {
        val numSats = satPosEcefMeters.numRows()
        val geometryMatrix = SimpleMatrix(numSats, 4)
        for (i in 0 until numSats) {
            val dPos = doubleArrayOf(
                satPosEcefMeters[i, 0] - posEcef[0],
                satPosEcefMeters[i, 1] - posEcef[1],
                satPosEcefMeters[i, 2] - posEcef[2],
            )
            val norm = sqrt(dPos[0] * dPos[0] + dPos[1] * dPos[1] + dPos[2] * dPos[2])
            for (j in 0 until 3) {
                geometryMatrix[i, j] = (posEcef[j] - satPosEcefMeters[i, j]) / norm
            geometryMatrix[i, 3] = 1.0
            }
        }
        return geometryMatrix
    }

    fun calculateHMatrix(weightMatrix: SimpleMatrix, geometryMatrix: SimpleMatrix): SimpleMatrix {
        val tempH = geometryMatrix.transpose().mult(weightMatrix).mult(geometryMatrix)
        val hMatrix = tempH.pseudoInverse()
        return hMatrix
    }

    fun matrixByColVecMult(matrix: SimpleMatrix, vector: SimpleMatrix): SimpleMatrix {
        val matrixLen = matrix.numRows()
        val vectorLen = vector.numRows()
        val result = SimpleMatrix(matrixLen, 1)

        for (i in 0 until matrixLen) {
            for (j in 0 until vectorLen) {
                result[i] += matrix[i, j] * vector[j]
            }
        }

        return result
    }

//    fun calculateGpsPosition(navData: NavData, obsData: ObsDataWithRange, rxClockError: Double): Pair<Xyz, Double> {
//        // Time from ephemeris reference epoch (tk)
//        val transmissionSec = obsData.pseudorange / Const.c
//        val t = obsData.inner.rxTimeNs - transmissionSec - rxClockError
//        val tk = timeDiffK(t, navData.t0)
//        val satelliteClockError = navData.satelliteClockErrorSec(tk)
//
//        // Mean motion (n)
//        val n = navData.meanMotion()
//
//        // Mean anomaly (M)
//        val M = navData.meanAnomaly(n, tk)
//
//        // Eccentric anomaly (E)
//        val E = navData.eccAnomaly(M)
//
//        // True anomaly (θ)
//        val theta = navData.trueAnomaly(E)
//
//        // Argument of latitude (φ)
//        var phi= navData.argumentOfLatitude(theta)
//        // Orbit radius of GPS satellite position (r)
//        var r = navData.orbitRadius(E)
//        // Corrected GPS orbit inclination (i)
//        var i0 = navData.i0
//
//        r += navData.radiusCorrection(phi)
//        phi += navData.argumentOfLatitudeCorrection(phi)
//        i0 += navData.inclinationCorrection(phi)
//
//        val (x, y) = gpsPositionInOrbitalPlane(r, phi)
//        val omegaK = navData.longitudeOfAscendingNode(tk, obsData.pseudorange)
//        val satPos = gpsPositionEcef(x, y, omegaK, i0)
//
//        // Correct GPS Satellite positions with earth's rotation
//        val rotatedAngle = Const.OmegaE * transmissionSec
//        val newX = satPos.x * cos(rotatedAngle) + satPos.y * sin(rotatedAngle)
//        val newY = satPos.x * -sin(rotatedAngle) + satPos.y * cos(rotatedAngle)
//        satPos.x = newX
//        satPos.y = newY
//
//        return satPos to satelliteClockError
//    }
//
//    fun geometricDistance(basePos: SimpleMatrix, gpsPosList: SimpleMatrix): SimpleMatrix {
//        Log.d("GNSS", "$basePos")
//        Log.d("GNSS", "$gpsPosList")
//        val numSats = gpsPosList.numRows()
//        val rho = SimpleMatrix(numSats, 1)
//        for (i in 0 until numSats) {
//            val x = basePos[0] - gpsPosList[i, 0]
//            val y = basePos[1] - gpsPosList[i, 1]
//            val z = basePos[2] - gpsPosList[i, 2]
//            rho[i] = sqrt(x * x + y * y + z * z)
//        }
//        return rho
//    }
//
//    fun designMatrix(basePos: SimpleMatrix, gpsPosList: SimpleMatrix, rho: SimpleMatrix): SimpleMatrix {
//        // Or the "unit vector", â = a / |a| where a is dx, dy, dz, 1, and |a| is rho
//        val numSats = gpsPosList.numRows()
//        val b = SimpleMatrix(numSats, 4)
//        for (i in 0 until numSats) {
//            b[i, 0] = basePos[0] - gpsPosList[i, 0] / rho[i]
//            b[i, 1] = basePos[1] - gpsPosList[i, 1] / rho[i]
//            b[i, 2] = basePos[2] - gpsPosList[i, 2] / rho[i]
//            b[i, 3] = 1.0
//        }
//        return b
//    }
//
//    fun gpsSatellitesInEnu(basePos: Xyz, gpsPosList: SimpleMatrix): SimpleMatrix {
//        // The transformation from global geodetic to local geodetic coordinate system
//        // can be calculated as below:
//        //
//        // [ -sin(λ)        cos(λ)        0      ]   [X_sat - X0]
//        // [ -sin(φ)cos(λ) -sin(φ)sin(λ)  cos(φ) ] * [Y_sat - Y0]
//        // [  cos(φ)cos(λ)  cos(φ)sin(λ)  sin(φ) ]   [Z_sat - Z0]
//        //
//        // where X0, Y0, Z0, φ, λ are the base position parameters
//
//        val (x, y, z) = basePos
//        val (phi, lam, _) = basePos.toPhiLamH()
//        val sinPhi = sin(phi)
//        val cosPhi = cos(phi)
//        val sinLam = sin(lam)
//        val cosLam = cos(lam)
//        val numSats = gpsPosList.numRows()
//        val gpsSatEnu = SimpleMatrix(numSats, 3)
//        for (i in 0 until numSats) {
//            gpsSatEnu[i, 0] = ((gpsPosList[i, 0] - x) * -sinLam
//                             + (gpsPosList[i, 1] - y) * cosLam)
//            gpsSatEnu[i, 1] = ((gpsPosList[i, 0] - x) * -sinPhi * cosLam
//                             + (gpsPosList[i, 1] - y) * -sinPhi * sinLam
//                             + (gpsPosList[i, 2] - z) * cosPhi)
//            gpsSatEnu[i, 2] = ((gpsPosList[i, 0] - x) * cosPhi * cosLam
//                             + (gpsPosList[i, 1] - y) * cosPhi * sinLam
//                             + (gpsPosList[i, 2] - z) * sinPhi)
//        }
//        return gpsSatEnu
//    }
//
//    fun elevationAngleFromEnu(gpsSatEnu: SimpleMatrix): SimpleMatrix {
//        // the elevation angle (radians) can be calculated as follows:
//        // d = sqrt(E ^ 2 + N ^ 2)
//        // theta = atan(U / d)
//        val numSats = gpsSatEnu.numRows()
//        val elevs = SimpleMatrix(numSats, 1)
//        for (i in 0 until numSats) {
//            val e = gpsSatEnu[i, 0]
//            val n = gpsSatEnu[i, 1]
//            val u = gpsSatEnu[i, 2]
//            val d = sqrt(e * e + n * n)
//            elevs[i] = atan2(u, d)
//        }
//        return elevs
//    }
//
//    fun klobucharAdjustment(basePos: Xyz, gpsPosList: SimpleMatrix): SimpleMatrix {
//        val numSats = gpsPosList.numRows()
//        val adjustments = SimpleMatrix(numSats, 1)
//        val gpsSatEnu = gpsSatellitesInEnu(basePos, gpsPosList)
//        val elevs = elevationAngleFromEnu(gpsSatEnu)
//
//        // slant factor
//        // (radians / pi) is the number of half-turns
//        // 5 light-nanoseconds slant distance (m)
//        for (i in 0 until numSats) {
//            adjustments[i] = (1.0 + 16.0 * (0.53 - elevs[i] / PI).pow(3)) * Const.c * 5e-9
//        }
//        return adjustments
//    }
}