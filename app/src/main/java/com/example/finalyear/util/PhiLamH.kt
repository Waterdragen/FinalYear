package com.example.finalyear.util

import org.ejml.simple.SimpleMatrix
import kotlin.math.*

data class PhiLamH (var phi: Double, var lam: Double, var h: Double) {
    fun rotationMatrix(): SimpleMatrix {
        val rotationMatrix = SimpleMatrix(3, 3)
        rotationMatrix[0, 0] = -sin(lam)
        rotationMatrix[1, 0] = -cos(lam) * sin(phi)
        rotationMatrix[2, 0] = cos(lam) * cos(phi)
        rotationMatrix[0, 1] = cos(lam)
        rotationMatrix[1, 1] = -sin(phi) * sin(lam)
        rotationMatrix[2, 1] = cos(phi) * sin(lam)
        rotationMatrix[0, 2] = 0.0
        rotationMatrix[1, 2] = cos(phi)
        rotationMatrix[2, 2] = sin(phi)
        return rotationMatrix
    }

    fun toXyz(ellipsoid: Ellipsoid): Xyz {
        val N = ellipsoid.primeVerticalN(phi)
        val x = (N + h) * cos(phi) * cos(lam)
        val y = (N + h) * cos(phi) * sin(lam)
        val z = ((1 - ellipsoid.ecc2) * N + h) * sin(phi)
        return Xyz(x, y, z)
    }
}