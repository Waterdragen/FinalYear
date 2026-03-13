package com.example.finalyear.core

import org.ejml.simple.SimpleMatrix

data class PositionAndRangeResidual(
    val satPrns: List<Int>,
    val satPosMeters: SimpleMatrix,
    val deltaRangeMeters: SimpleMatrix,
    val covarMatrixMetersSq: SimpleMatrix,
)