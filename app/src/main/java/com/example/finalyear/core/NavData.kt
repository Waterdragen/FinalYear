package com.example.finalyear.core

import android.location.cts.nano.Ephemeris
import android.location.cts.nano.Ephemeris.GpsEphemerisProto
import android.location.cts.suplClient.SuplRrlpController
import com.example.finalyear.math.Const
import com.example.finalyear.math.MathFn
import com.example.finalyear.model.Ionospheric
import com.example.finalyear.coord.Xyz
import com.example.finalyear.math.GpsTime
import org.joda.time.DateTime
import kotlin.math.*

data class NavData(
    val prn: Int,
    var dateTime: DateTime = DateTime(1, 1, 1, 0, 0, 0),
    var svClockBias: Double = 0.0,
    var svClockDrift: Double = 0.0,
    var svClockDriftRate: Double = 0.0,
    var iode: Int = 0,
    var crs: Double = 0.0,
    var deltaN: Double = 0.0,
    var m0: Double = 0.0,
    var cuc: Double = 0.0,
    var ecc: Double = 0.0,
    var cus: Double = 0.0,
    var sqrtA: Double = 0.0,
    var t0: Double = 0.0,
    var cic: Double = 0.0,
    var omega0: Double = 0.0,
    var cis: Double = 0.0,
    var i0: Double = 0.0,
    var crc: Double = 0.0,
    var omega: Double = 0.0,
    var dOmega: Double = 0.0,
    var dI: Double = 0.0,
    var codeL2: Double = 0.0,
    var weekNo: Double = 0.0,
    var l2PDataFlag: Double = 0.0,
    var svAccuracy: Double = 0.0,
    var svHealth: Double = 0.0,
    var tgd: Double = 0.0,
    var iodc: Int = 0,
    var transmissionTimeOfMsg: Double = 0.0,
    var fitInterval: Double = 0.0,
    var spare1: Double = 0.0,
    var spare2: Double = 0.0,

    var iono: Ionospheric = Ionospheric(),
) {
    companion object {
        fun fromGpsEphemerisProto(ephem: GpsEphemerisProto): NavData {
            return NavData(
                prn = ephem.prn,
                dateTime = GpsTime.fromWeekAndTowSec(ephem.week.toDouble(), ephem.toe).toDatetime(),
                svClockBias = ephem.af0,
                svClockDrift = ephem.af1,
                svClockDriftRate = ephem.af2,
                iode = ephem.iode,
                crs = ephem.crs,
                deltaN = ephem.deltaN,
                m0 = ephem.m0,
                cuc = ephem.cuc,
                ecc = ephem.e,
                cus = ephem.cus,
                sqrtA = ephem.rootOfA,
                t0 = ephem.toe,
                cic = ephem.cic,
                omega0 = ephem.omega0,
                cis = ephem.cis,
                i0 = ephem.i0,
                crc = ephem.crc,
                omega = ephem.omega,
                dOmega = ephem.omegaDot,
                dI = ephem.iDot,
                codeL2 = ephem.l2Code.toDouble(),
                weekNo = ephem.week.toDouble(),
                l2PDataFlag = ephem.l2Flag.toDouble(),
                svAccuracy = ephem.svAccuracyM,
                svHealth = ephem.svHealth.toDouble(),
                tgd = ephem.tgd,
                iodc = ephem.iodc,
                transmissionTimeOfMsg = ephem.toe,
                fitInterval = ephem.fitInterval,
                iono = Ionospheric()
            )
        }

        fun fetchEphemerisFromSupl(): List<NavData> {
            val controller = SuplRrlpController("supl.google.com", 7276)

            val navProto: Ephemeris.GpsNavMessageProto = controller.generateNavMessage(22L * 10_000_000L, 114L * 10_000_000L)

            val iono = Ionospheric(navProto.iono.alpha, navProto.iono.beta)

            return navProto.ephemerids
                .filterNotNull()
                .map {
                    val navData = fromGpsEphemerisProto(it)
                    navData.iono = iono.clone()  // fill ionospheric data
                    navData
                }
        }
    }

    fun gpsTimeSecs(): Double {
        return weekNo * Const.WEEK_SEC + t0
    }

    fun meanMotion(): Double {
        // n = sqrt(mu / a^3)
        //   = sqrt(mu) / sqrt(a^3)
        //   = sqrt(mu) / sqrt(a) ^ 3
        // n = n0 + Δn
        return sqrt(Const.Mu) / sqrtA.pow(3) + deltaN
    }

    fun meanAnomaly(n: Double, tk: Double): Double {
        // Mk = m0 + tk * (n0 + Δn)
        return m0 + tk * n
    }

    fun eccAnomaly(m: Double): Double {
        // E = M + e * sin(E)
        // firstly, assume e * sin(M) is very small ~= 0
        // E = M
        // then we can work bottom-up by substituting E to get the new E
        var iterCount = 0
        var e = m
        var oldE = e
        var newE = Double.POSITIVE_INFINITY
        while (abs(newE - oldE) > 1e-11) {
            oldE = e
            newE = m + ecc * sin(e)
            e = newE
            iterCount += 1
            if (iterCount > 100) {
                throw RuntimeException("eccAnomaly failed to converge with error: ${newE - oldE}")
            }
        }

        return e
    }

    fun trueAnomaly(E: Double): Double {
        // θ = 2 * atan(sqrt(1 + e) / sqrt(1 - e) * tan(E / 2))
        return 2 * atan(sqrt((1 + ecc) / (1 - ecc)) * tan(E / 2))
    }

    fun argumentOfLatitude(theta: Double): Double {
        return theta + omega
    }

    fun orbitRadius(E: Double): Double {
        val r = sqrtA * sqrtA * (1 - ecc * cos(E))
        return r
    }

    fun argumentOfLatitudeCorrection(phi: Double): Double {
        return cuc * cos(2 * phi) + cus * sin(2 * phi)
    }

    fun radiusCorrection(phi: Double): Double {
        return crc * cos(2 * phi) + crs * sin(2 * phi)
    }

    fun inclinationCorrection(phi: Double): Double {
        return cic * cos(2 * phi) + cis * sin(2 * phi)
    }

    fun longitudeOfAscendingNode(tk: Double, rangeMeters: Double): Double {
        return omega0 + (dOmega - Const.OmegaE) * tk - Const.OmegaE * (t0 + rangeMeters / Const.c)
    }

    fun satelliteClockErrorSec(tk: Double): Double {
        return svClockBias + svClockDrift * tk + svClockDriftRate * tk * tk - tgd
    }

    fun relativisticCorrSec(e: Double): Double {
        return Const.RelativisticF * ecc * sqrtA * sin(e)
    }

    fun calculateSatPosAndClock(obsData: ObsDataWithRange,
                                approxPseudorange: Double,
                                rxClockErrSec: Double): Pair<Xyz, Double> {
        val transmissionTime = approxPseudorange / Const.c
        var t = obsData.inner.rxTimeNs * 1e-9 - transmissionTime - rxClockErrSec
        var tk = MathFn.fixWeekRollover(t - t0)

        // Satellite clock error
        var satClockErrSec = satelliteClockErrorSec(tk)
        t += satClockErrSec
        tk = MathFn.fixWeekRollover(t - t0)

        // Mean motion (n) and Mean anomaly (M)
        val n = meanMotion()
        val m = meanAnomaly(n, tk)

        // Eccentric anomaly (E) and True anomaly (θ)
        val e = eccAnomaly(m)
        val theta = trueAnomaly(e)

        // Argument of latitude (φ), Orbit radius (r), inclination (i)
        var phi = argumentOfLatitude(theta)
        var r = orbitRadius(e)
        var i = i0 + dI * tk

        // Corrected argument of latitude (φ), Orbit radius (r), inclination (i)
        phi += argumentOfLatitudeCorrection(phi)
        r += radiusCorrection(phi)
        i += inclinationCorrection(phi)

        // GPS Positions in the orbital plane
        val (x0, y0) = MathFn.gpsPosOrbitalPlane(r, phi)

        // Longitude of ascending node (Ω_k)
        val omegaK = longitudeOfAscendingNode(tk, approxPseudorange)

        val pos = MathFn.gpsPosEcef(x0, y0, omegaK, i)

        // Satellite clock error + relativistic correction
        satClockErrSec = satelliteClockErrorSec(tk)
        val relCorrectionSec = relativisticCorrSec(e)
        val satClockErrM = (satClockErrSec + relCorrectionSec) * Const.c

        return Pair(pos, satClockErrM)
    }
}