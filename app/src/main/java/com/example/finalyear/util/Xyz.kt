package com.example.finalyear.util

import com.example.finalyear.MathFn
import org.ejml.simple.SimpleMatrix
import kotlin.math.*

data class Xyz (var x: Double, var y: Double, var z: Double) {
    companion object {
        private const val ECEF_NEAR_POLE_THRESHOLD = 1.0

        fun from(doubleArray: DoubleArray): Xyz {
            return Xyz(doubleArray[0], doubleArray[1], doubleArray[2])
        }

        fun from(vector: SimpleMatrix): Xyz {
            return Xyz(vector[0], vector[1], vector[2])
        }
    }

    // Closed form Xyz to LLA
    // Bowring, B. R. (1976). Transformation from spatial to geographical coordinates. Survey Review, 23(181), 323–327. https://doi.org/10.1179/sre.1976.23.181.323
    fun toPhiLamH(ellipsoid: Ellipsoid = Wgs84): PhiLamH {
        val a = ellipsoid.a
        val ecc2 = ellipsoid.ecc2
        val b = ellipsoid.b
        val eccPrime2 = ellipsoid.eccPrime2

        val p = sqrt(x * x + y * y)
        val theta = atan2(a * z,b * p)

        val lam = atan2(y, x).mod(2 * Math.PI)

        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        val atanNumer = z + eccPrime2 * b * sinTheta.pow(3.0)
        val atanDenom = p - ecc2 * a * cosTheta.pow(3.0)
        val phi = atan2(atanNumer, atanDenom).mod(2 * Math.PI)

        val N = ellipsoid.primeVerticalN(phi)
        var h = p / cos(phi) - N

        // Optional: altitude near poles, because cos(lat) -> 0 as lat -> 90deg
        val polesCheck = (abs(x) < ECEF_NEAR_POLE_THRESHOLD
                         && abs(y) < ECEF_NEAR_POLE_THRESHOLD)
        if (polesCheck) {
            h = abs(z) - b
        }

        return PhiLamH(phi, lam, h)  // phi and lam are in radians
    }

    fun toTopocentricAED(dst: Xyz): TopocentricAED {
        return TopocentricAED.fromXyzLine(this, dst)
    }
}