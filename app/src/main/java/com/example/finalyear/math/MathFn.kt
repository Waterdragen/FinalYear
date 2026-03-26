package com.example.finalyear.math

import com.example.finalyear.coord.Xyz
import org.ejml.simple.SimpleMatrix
import org.joda.time.DateTime
import kotlin.math.*

object MathFn {
    fun fixTowNsRollover(towNs: Long): Long {
        if (towNs > Const.HALF_WEEK_NS) {
            return towNs - 2 * Const.HALF_WEEK_NS
        }
        if (towNs < -Const.HALF_WEEK_NS) {
            return towNs + 2 * Const.HALF_WEEK_NS
        }
        return towNs
    }
    fun fixWeekRollover(towSec: Double): Double {
        require(towSec < 1e9) { "passed argument to towSec: $towSec"}  // Avoid accidental ns

        if (towSec > Const.HALF_WEEK_SEC) {
            return towSec - 2 * Const.HALF_WEEK_SEC
        }
        if (towSec < -Const.HALF_WEEK_SEC) {
            return towSec + 2 * Const.HALF_WEEK_SEC
        }
        return towSec
    }

    // Converts datetime to seconds since GPS Epoch without leap second considerations
    fun datetimeToGpsTime(dt: DateTime): Double {
        val secsSinceUnixEpoch: Long = dt.millis / 1000L
        val secsSinceGpsEpoch: Long = secsSinceUnixEpoch - 315_964_800L
        return secsSinceGpsEpoch.toDouble()
    }

    fun gpsPosOrbitalPlane(r: Double, phiC: Double): Pair<Double, Double> {
        val x0 = r * cos(phiC)
        val y0 = r * sin(phiC)
        return x0 to y0
    }

    fun gpsPosEcef(x0: Double, y0: Double, omegaK: Double, i: Double): Xyz {
        val x = x0 * cos(omegaK) - y0 * cos(i) * sin(omegaK)
        val y = x0 * sin(omegaK) + y0 * cos(i) * cos(omegaK)
        val z = y0 * sin(i)
        return Xyz(x, y, z)
    }

    fun expectedPseudorange(userPos: Xyz, satPos: Xyz): Double {
        val dx = userPos.x - satPos.x
        val dy = userPos.y - satPos.y
        val dz = userPos.z - satPos.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun expectedPseudoranges(basePos: Xyz, gpsPosList: SimpleMatrix): SimpleMatrix {
        val rows = gpsPosList.numRows()
        val rho = SimpleMatrix(rows, 1)

        for (i in 0 until rows ) {
            val dx = basePos.x - gpsPosList[i, 0]
            val dy = basePos.y - gpsPosList[i, 1]
            val dz = basePos.z - gpsPosList[i, 2]
            rho[i] = sqrt(dx * dx + dy * dy + dz * dz)
        }
        return rho
    }
    fun designMatrix(basePos: Xyz, gpsPosList: SimpleMatrix, rho: SimpleMatrix): SimpleMatrix {
        // Or the "unit vector", â = a / |a| where a is dx, dy, dz, 1, and |a| is rho
        val rows = gpsPosList.numRows()
        val b = SimpleMatrix(rows, 4)
        for (i in 0 until rows) {
            b[i, 0] = (basePos.x - gpsPosList[i, 0]) / rho[i]
            b[i, 1] = (basePos.y - gpsPosList[i, 1]) / rho[i]
            b[i, 2] = (basePos.z - gpsPosList[i, 2]) / rho[i]
            b[i, 3] = 1.0
        }
        return b
    }
}