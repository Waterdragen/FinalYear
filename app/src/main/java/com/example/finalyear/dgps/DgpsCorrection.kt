package com.example.finalyear.dgps

import kotlinx.serialization.Serializable

@Serializable
data class DgpsCorrection(
    val prc: Double,
    val rrc: Double,
    val iod: Int,
    val udre: Int
)