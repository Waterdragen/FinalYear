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

        val lam = atan2(y, x) % (2 * Math.PI)

        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        val atanNumer = z + eccPrime2 * b * sinTheta.pow(3.0)
        val atanDenom = p - ecc2 * a * cosTheta.pow(3.0)
        val phi = atan2(atanNumer, atanDenom) % (2 * Math.PI)

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
        // Calculate elevation and azimuth
        val originWgs = this.toPhiLamH()

        val deltaPos = SimpleMatrix(3, 1)
        deltaPos[0] = dst.x - this.x
        deltaPos[1] = dst.y - this.y
        deltaPos[2] = dst.z - this.z

        val enuVectorMeters = MathFn.matrixByColVecMult(originWgs.rotationMatrix(), deltaPos)
        val east = enuVectorMeters[0]
        val north = enuVectorMeters[1]
        val up = enuVectorMeters[2]

        // - calculate azimuth, elevation and height from the enu values
        val horizontalDistance = hypot(east, north)
        var azimuth: Double
        val elevationRadians: Double

        if (horizontalDistance < 1e-22) {
            elevationRadians = PI / 2.0
            azimuth = 0.0
        } else {
            elevationRadians = atan2(up, horizontalDistance)
            azimuth = atan2(east, north)
        }
        if (azimuth < 0.0) {
            azimuth += 2 * PI
        }
        val distance = sqrt(
            deltaPos[0] * deltaPos[0]
            + deltaPos[1] * deltaPos[1]
            + deltaPos[2] * deltaPos[2]
        )

        return TopocentricAED(
            azimuth = azimuth,
            elevation = elevationRadians,
            distance = distance,
        )
    }
}