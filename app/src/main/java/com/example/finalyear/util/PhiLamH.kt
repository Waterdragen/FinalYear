package com.example.finalyear.util

import org.ejml.simple.SimpleMatrix
import kotlin.math.*

data class PhiLamH (var phi: Double, var lam: Double, var h: Double) {
    fun rotationMatrix(): SimpleMatrix {
        val sinPhi = sin(phi)
        val sinLam = sin(lam)
        val cosPhi = cos(phi)
        val cosLam = cos(lam)

        return SimpleMatrix(3, 3).apply {
            this[0, 0] = -sinLam; this[0, 1] = cosLam;  this[0, 2] = 0.0
            this[1, 0] = -sinPhi * cosLam
            this[1, 1] = -sinPhi * sinLam
            this[1, 2] = cosPhi
            this[2, 0] = cosPhi * cosLam
            this[2, 1] = cosPhi * sinLam
            this[2, 2] = sinPhi
        }
    }

    fun toXyz(ellipsoid: Ellipsoid): Xyz {
        val N = ellipsoid.primeVerticalN(phi)
        val x = (N + h) * cos(phi) * cos(lam)
        val y = (N + h) * cos(phi) * sin(lam)
        val z = ((1 - ellipsoid.ecc2) * N + h) * sin(phi)
        return Xyz(x, y, z)
    }

    fun degPretty(): String {
        val latD = radToDegNormalized(phi)
        val lonD = radToDegNormalized(lam)
        return "(Lat %.9f, Lon %.9f, H %.3f)".format(latD, lonD, h)
    }

    fun dmsPretty(): String {
        val (latD, latM, latS) = radiansToDms(phi)
        val (lonD, lonM, lonS) = radiansToDms(lam)
        return "(Lat %d°%d'%.4f\", Lon %d°%d'%.4f, H %.3f)".format(latD, latM, latS, lonD, lonM, lonS, h)
    }
}

private fun radToDegNormalized(rad: Double): Double {
    var d = Math.toDegrees(rad) % 360.0
    if (d > 180.0) d -= 360.0
    return d
}

private fun radiansToDms(rad: Double): Triple<Int, Int, Double> {
    val deg = radToDegNormalized(rad)
    val d = deg.toInt()
    val mins = (deg - d) * 60.0
    val m = mins.toInt()
    val secs = (mins - m) * 60.0
    return Triple(d, m, secs)
}