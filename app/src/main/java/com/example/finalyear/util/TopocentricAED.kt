package com.example.finalyear.util

import org.ejml.simple.SimpleMatrix
import kotlin.math.*

data class TopocentricAED(
    val azimuth: Double,
    val elevation: Double,
    val distance: Double
) {
    companion object {
        fun fromXyzLine(src: Xyz, dst: Xyz): TopocentricAED {
            val originWgs = src.toPhiLamH()

            val deltaPos = SimpleMatrix(3, 1).apply {
                this[0] = dst.x - src.x
                this[1] = dst.y - src.y
                this[2] = dst.z - src.z
            }

            val rotationMatrix = originWgs.rotationMatrix()
            val enuVectors = rotationMatrix.mult(deltaPos)

            val east = enuVectors[0]
            val north = enuVectors[1]
            val up = enuVectors[2]

            val horizontalDistance = hypot(east, north)
            var azimuth = 0.0
            var elevation = PI / 2.0

            if (horizontalDistance >= 1e-22) {
                azimuth = atan2(east, north)
                elevation = atan2(up, horizontalDistance)
            }
            if (azimuth < 0.0) azimuth += 2 * PI

            val distance = sqrt(
                deltaPos[0] * deltaPos[0] +
                        deltaPos[1] * deltaPos[1] +
                        deltaPos[2] * deltaPos[2]
            )

            return TopocentricAED(azimuth, elevation, distance)
        }
    }
}