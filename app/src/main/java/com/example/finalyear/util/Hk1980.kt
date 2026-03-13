package com.example.finalyear.util

import android.util.Log
import kotlin.math.*

object Hk1980: Ellipsoid {
    override val a = 6_378_388.0
    override val ecc2 = 6.722670022e-3
    override val b = sqrt(a * a * (1 - ecc2))
    override val eccPrime2 = (a * a - b * b) / (b * b)

    // Helmert 7-parameter transformation
    // https://www.geodetic.gov.hk/common/data/parameter/7P_ITRF96_HK80_V1.pdf
    const val tx = 162.619
    const val ty = 276.961
    const val tz = 161.763
    val rx = radians(0.067741 / 3600.0)  // arcsecond to radians
    val ry = radians(-2.243649 / 3600.0)
    val rz = radians(-1.158827 / 3600.0)
    const val scale = 1 + 1.094239e-6

    fun fromWgs84Xyz(wgs: Xyz): Xyz {
        val x = tx + scale * wgs.x + rz * wgs.y - ry * wgs.z
        val y = ty - rz * wgs.x + scale * wgs.y + rx * wgs.z
        val z = tz + ry * wgs.x - rx * wgs.y + scale * wgs.z
        return Xyz(x, y, z)
    }

    // Hk1980 Grid parameters
    // https://www.geodetic.gov.hk/common/data/pdf/explanatorynotes.pdf
    val phi0 = dmsInRadians(22.0, 18.0, 43.68)
    val lam0 = dmsInRadians(114.0, 10.0, 42.80)
    const val falseN = 819069.80
    const val falseE = 836694.05
    const val k0 = 1.0  // scale factor on the central meridian
    const val M0 = 2_468_395.728  // meridian radius of curvature, equator to origin

    // Meridian distance coefficients
    private val ecc4 = ecc2 * ecc2
    private val a0 = 1.0 - ecc2/4.0 - 3.0*ecc4/64.0
    private val a2 = 3.0/8.0 * (ecc2 + ecc4/4.0)
    private val a4 = 15.0/256.0 * ecc4
    private fun meridianDistance(phi: Double): Double {
        return a * (a0 * phi - a2 * sin(2 * phi) + a4 * sin(4 * phi))
    }

    fun hk80LlaToGrid(hk80Lla: PhiLamH): Grid {
        val (phi, lam, h) = hk80Lla
        val dLam = lam - lam0
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val M = meridianDistance(phi)
        val N = primeVerticalN(phi)
        val rho = meridianRadiusOfCurvature(phi)
        val psi = N / rho

        val easting = falseE + k0 * (
                    N * dLam * cosPhi
                    + N * (dLam.pow(3) / 6.0) * cosPhi.pow(3) * (psi - tanPhi.pow(2)))
        val northing = falseN + k0 * (
                    (M - M0) + N * sinPhi * (dLam.pow(2) / 2.0) * cosPhi)
        return Grid(easting, northing, h)
    }

    data class Grid(val e: Double, val n: Double, val h: Double) {
        companion object {
            fun fromWgs84Xyz(wgsXyz: Xyz): Grid {
                val hk80Xyz = Hk1980.fromWgs84Xyz(wgsXyz)
                val hk80Lla = hk80Xyz.toPhiLamH(Hk1980)
                val hk80Grid = hk80LlaToGrid(hk80Lla)
                return hk80Grid
            }
        }
    }

    private fun radians(deg: Double): Double {
        return deg * PI / 180.0
    }

    private fun dmsInRadians(d: Double, m: Double, s: Double): Double {
        return radians(d + m / 60.0 + s / 3600.0)
    }
}

