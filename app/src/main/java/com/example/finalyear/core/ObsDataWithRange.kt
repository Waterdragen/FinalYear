package com.example.finalyear.core

data class ObsDataWithRange(
    val inner: ObsData,
    var pseudorange: Double,
    val uncertainty: Double,
) {
    fun clone(): ObsDataWithRange {
        val obsData = inner.clone()
        return ObsDataWithRange(
            inner=obsData,
            pseudorange=pseudorange,
            uncertainty=uncertainty,
        )
    }
}