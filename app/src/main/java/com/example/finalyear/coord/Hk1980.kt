package com.example.finalyear.coord

import kotlin.math.*

object Hk1980: Ellipsoid {
    override val a = 6_378_388.0
    override val ecc2 = 6.722670022e-3
    override val b = sqrt(a * a * (1 - ecc2))
    override val eccPrime2 = (a * a - b * b) / (b * b)

    // Helmert 7-parameter transformation
    // https://www.geodetic.gov.hk/common/data/parameter/7P_ITRF96_HK80_V1.pdf
    const val TX = 162.619
    const val TY = 276.961
    const val TZ = 161.763
    val RX = Math.toRadians(0.067741 / 3600.0)  // arcsecond to radians
    val RY = Math.toRadians(-2.243649 / 3600.0)
    val RZ = Math.toRadians(-1.158827 / 3600.0)
    const val SCALE = 1 + 1.094239e-6

    fun fromWgs84Xyz(wgs: Xyz): Xyz {
        val x = TX + SCALE * wgs.x + RZ * wgs.y - RY * wgs.z
        val y = TY - RZ * wgs.x + SCALE * wgs.y + RX * wgs.z
        val z = TZ + RY * wgs.x - RX * wgs.y + SCALE * wgs.z
        return Xyz(x, y, z)
    }

    // Hk1980 Grid parameters
    // https://www.geodetic.gov.hk/common/data/pdf/explanatorynotes.pdf
    val PHI0 = dmsInRadians(22.0, 18.0, 43.68)
    val LAM0 = dmsInRadians(114.0, 10.0, 42.80)
    const val FALSE_N = 819069.80
    const val FALSE_E = 836694.05
    const val k0 = 1.0  // scale factor on the central meridian
    const val M0 = 2_468_395.728  // meridian radius of curvature, equator to origin

    // Meridian distance coefficients
    private val ecc4 = ecc2 * ecc2
    private val A0 = 1.0 - ecc2/4.0 - 3.0*ecc4/64.0
    private val A2 = 3.0/8.0 * (ecc2 + ecc4/4.0)
    private val A4 = 15.0/256.0 * ecc4
    private fun meridianDistance(phi: Double): Double {
        return a * (A0 * phi - A2 * sin(2 * phi) + A4 * sin(4 * phi))
    }

    fun hk80LlaToGrid(hk80Lla: PhiLamH): Grid {
        val (phi, lam, h) = hk80Lla
        val dLam = lam - LAM0
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val M = meridianDistance(phi)
        val N = primeVerticalN(phi)
        val rho = meridianRadiusOfCurvature(phi)
        val psi = N / rho

        val easting = FALSE_E + k0 * (
                    N * dLam * cosPhi
                    + N * (dLam.pow(3) / 6.0) * cosPhi.pow(3) * (psi - tanPhi.pow(2)))
        val northing = FALSE_N + k0 * (
                    (M - M0) + N * sinPhi * (dLam.pow(2) / 2.0) * cosPhi)
        return Grid(easting, northing, h)
    }

    data class Grid(var e: Double, var n: Double, var h: Double) {
        companion object {
            fun fromWgs84Xyz(wgsXyz: Xyz): Grid {
                val hk80Xyz = Hk1980.fromWgs84Xyz(wgsXyz)
                val hk80Lla = hk80Xyz.toPhiLamH(Hk1980)
                val hk80Grid = hk80LlaToGrid(hk80Lla)
                return hk80Grid
            }
        }

        fun addAssign(other: Grid) {
            e += other.e
            n += other.n
            h += other.h
        }

        fun divNum(num: Double) {
            e /= num
            n /= num
            h /= num
        }
    }
}

private fun dmsInRadians(d: Double, m: Double, s: Double): Double =
    Math.toRadians(d + m / 60.0 + s / 3600.0)