package com.example.finalyear.model

import com.example.finalyear.math.Const
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsDataWithRange
import com.example.finalyear.coord.Xyz
import org.ejml.simple.SimpleMatrix
import kotlin.math.*

data class Ionospheric (
    var alpha: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0),
    var beta: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0, 0.0),
) {
    companion object {
        fun delays(navDataList: List<NavData>,
                   obsDataList: List<ObsDataWithRange>,
                   userPos: Xyz,
                   satPosList: SimpleMatrix,
                   frequencyHz: Double): SimpleMatrix {
            val numOfObs = obsDataList.size
            val ionoDelayList = SimpleMatrix(numOfObs, 1)
            for (i in 0 until numOfObs) {
                ionoDelayList[i] = navDataList[i].iono.klobucharDelayMeters(
                    userPosEcef = userPos,
                    satPosEcef = Xyz.fromMatrixRow(satPosList, row = i),
                    rxTowSec = obsDataList[i].inner.rxTimeNs * 1e-9,
                    frequencyHz = frequencyHz,
                )
            }
            return ionoDelayList
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Ionospheric) return false
        return alpha.contentEquals(other.alpha)
                && beta.contentEquals(other.beta)
    }

    override fun hashCode(): Int {
        var result = alpha.contentHashCode()
        result = 31 * result + beta.contentHashCode()
        return result
    }

    fun clone(): Ionospheric {
        return Ionospheric(
            alpha = alpha.clone(),
            beta = beta.clone(),
        )
    }

    fun clear() {
        alpha = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        beta = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    }

    fun klobucharDelayMeters(
        userPosEcef: Xyz,
        satPosEcef: Xyz,
        rxTowSec: Double,
        frequencyHz: Double,
    ): Double {
        val topocentricAED = userPosEcef.toTopocentricAED(satPosEcef)
        val azimuth = topocentricAED.azimuth
        val elevationRadians = topocentricAED.elevation
        val originWgs = userPosEcef.toPhiLamH()

        val elevationSemiCircle = elevationRadians / PI
        val azimuthSemiCircle = azimuth / PI
        val latUSemiCircle = originWgs.phi / PI
        val longUSemiCircle = originWgs.lam / PI

        // earth's centered angle (semi-circles)
        val earthCenteredAngleSemiCircle = 0.0137 / (elevationSemiCircle + 0.11) - 0.022

        // latitude of the Ionospheric Pierce Point (IPP) (semi-circles)
        var latISemiCircle = latUSemiCircle + earthCenteredAngleSemiCircle * cos(azimuthSemiCircle * PI)

        if (latISemiCircle > 0.416) {
            latISemiCircle = 0.416
        } else if (latISemiCircle < -0.416) {
            latISemiCircle = -0.416
        }

        // geodetic longitude of the Ionospheric Pierce Point (IPP) (semi-circles)
        val longISemiCircle = (longUSemiCircle
            + earthCenteredAngleSemiCircle
                * sin(azimuthSemiCircle * PI)
                / cos(latISemiCircle * PI))

        // geomagnetic latitude of the Ionospheric Pierce Point (IPP) (semi-circles)
        val geomLatIPPSemiCircle = latISemiCircle + 0.064 * cos(longISemiCircle * PI - 5.08)

        var localTimeSec = 86400.0 / 2.0 * longISemiCircle + rxTowSec
        localTimeSec %= 86400.0
        if (localTimeSec < 0) {
            localTimeSec += 86400.0
        }

        // amplitude of the ionospheric delay (seconds)
        val amplitudeOfDelaySeconds = max((
                alpha[0]
                + alpha[1] * geomLatIPPSemiCircle
                + alpha[2] * geomLatIPPSemiCircle * geomLatIPPSemiCircle
                + alpha[3]
                * geomLatIPPSemiCircle
                * geomLatIPPSemiCircle
                * geomLatIPPSemiCircle
        ), 0.0)

        // period of ionospheric delay
        val periodOfDelaySec = max((
                beta[0]
                + beta[1] * geomLatIPPSemiCircle
                + beta[2] * geomLatIPPSemiCircle * geomLatIPPSemiCircle
                + beta[3] * geomLatIPPSemiCircle * geomLatIPPSemiCircle * geomLatIPPSemiCircle
        ), 72000.0)

        // phase of ionospheric delay
        val phaseOfDelay = 2 * PI * (localTimeSec - 50400) / periodOfDelaySec

        // slant factor
        val slantFactor = 1.0 + 16.0 * (0.53 - elevationSemiCircle).pow(3)

        // ionospheric time delay (seconds)
        var ionoDelaySec = if (abs(phaseOfDelay) >= PI / 2.0) {
            5.0e-9 * slantFactor
        } else {
            (5.0e-9
                    + (1
                    - phaseOfDelay.pow(2) / 2.0
                    + phaseOfDelay.pow(4) / 24.0)
                    * amplitudeOfDelaySeconds) * slantFactor
        }

        // apply factor for frequency bands other than L1
        ionoDelaySec *= (Const.L1_FREQ_HZ * Const.L1_FREQ_HZ) / (frequencyHz * frequencyHz)

        return ionoDelaySec * Const.c
    }
}