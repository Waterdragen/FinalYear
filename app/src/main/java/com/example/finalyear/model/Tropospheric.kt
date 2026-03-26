package com.example.finalyear.model

import com.example.finalyear.math.Const
import com.example.finalyear.coord.Xyz
import org.ejml.simple.SimpleMatrix
import kotlin.math.*

object Tropospheric {
    data class MeteorologyStats(
        val pressureHpa: Double,
        val tempKelvin: Double,
        val waterVaporPressureHpa: Double,
    ) {
        companion object {
            fun fromRh(pressureHpa: Double, tempKelvin: Double, rh: Double): MeteorologyStats {
                val saturationVaporPressure = saturationVaporPressureHpa(tempKelvin)
                return MeteorologyStats(
                    pressureHpa = pressureHpa,
                    tempKelvin = tempKelvin,
                    waterVaporPressureHpa = saturationVaporPressure * rh)
            }
            private fun saturationVaporPressureHpa(tempKelvin: Double): Double {
                val tC = tempKelvin - 273.15
                return 6.112 * exp((17.62 * tC) / (243.12 + tC)) // Magnus, hPa
            }
        }
        // Zenith hydrostatiic delay
        fun saasZhdMeters(latRad: Double, heightMeters: Double): Double {
            val f = 1.0 - 0.00266 * cos(2.0 * latRad) - 0.00000028 * heightMeters
            return 0.0022768 * pressureHpa / f
        }

        // Zenith wet delay
        fun saasZwdMeters(): Double {
            return 0.002277 * (1255.0 / tempKelvin + 0.05) * waterVaporPressureHpa
        }
    }

    val assumedMetStats = MeteorologyStats.fromRh(
        pressureHpa = 1013.25,
        tempKelvin = 300.0,  // 27°C
        rh = 0.75,  // 75% relative humidity
    )

    fun delays(userPos: Xyz,
               satPosList: SimpleMatrix): SimpleMatrix {
        val numRows = satPosList.numRows()
        val troposphericDelayList = SimpleMatrix(numRows, 1)
        for (i in 0 until numRows) {
            val satPos = Xyz.fromMatrixRow(satPosList, row = i)
            val tropoDelay = saastamoinenDelayMeters(userPos, satPos)
            troposphericDelayList[i] = tropoDelay
        }
        return troposphericDelayList
    }

    fun saastamoinenDelayMeters(
        userPos: Xyz,
        satPos: Xyz,
    ): Double {
        val elevationRad = userPos.toTopocentricAED(satPos)
            .elevation
        if (!elevationRad.isFinite() || elevationRad <= 0.0) {
            return 0.0  // No adjustments for invalid values
        }
        val originWgs = userPos.toPhiLamH()
        val geoidHeight = 0.0  // TODO: calculate geoid height
        val heightAboveSeaLevel = originWgs.h - geoidHeight

        val (mDry, mWet) = computeDryAndWetMappingValuesUsingUNBabcMappingFunction(
            latRad = originWgs.phi,
            satElevationRadians = elevationRad,
            heightMeters = heightAboveSeaLevel,
        )

        val zhd = assumedMetStats.saasZhdMeters(
            latRad = originWgs.phi,
            heightMeters = heightAboveSeaLevel,
        )
        val zwd = assumedMetStats.saasZwdMeters()

        // Slant delay
        val slantDelayMeters = mDry * zhd + mWet * zwd

        return slantDelayMeters
    }

    fun computeDryAndWetMappingValuesUsingUNBabcMappingFunction(
        latRad: Double,
        satElevationRadians: Double,
        heightMeters: Double,
    ): Pair<Double, Double> {
        val elevation = satElevationRadians.coerceIn(Math.toRadians(2.0), Math.PI / 2.0)  // clamp between 2 and 90 degrees
        val sinE = sin(elevation)
        val cosLat = cos(latRad)
        val heightKm = heightMeters / 1000.0

        // dry components mapping parameters
        val aHydrostatic =  (1.18972 - 0.026855 * heightKm + 0.10664 * cosLat) / 1000.0

        // wet components mapping parameters
        val aNonHydrostatic = (0.61120 - 0.035348 * heightKm - 0.01526 * cosLat) / 1000.0

        val dryMap = abcMapping(sinE = sinE, a = aHydrostatic, b = Const.B_HYDROSTATIC, c = Const.C_HYDROSTATIC)
        val wetMap = abcMapping(sinE = sinE, a = aNonHydrostatic, b = Const.B_NON_HYDROSTATIC, c = Const.C_NON_HYDROSTATIC)

        return Pair(dryMap, wetMap)
    }

    private fun abcMapping(sinE: Double, a: Double, b: Double, c: Double): Double {
        val numerator = 1.0 + a / (1.0 + b / (1.0 + c))
        val denominator = sinE + a / (sinE + b / (sinE + c))
        return numerator / denominator
    }
}