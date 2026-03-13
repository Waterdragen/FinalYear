package com.example.finalyear.util

import kotlin.math.*

interface Ellipsoid {
    val a: Double  // semi-major axis
    val ecc2: Double  // eccentricity squared
    val b: Double  // semi-minor axis
    val eccPrime2: Double  // second eccentricity squared

    // prime vertical radius of curvature
    fun primeVerticalN(phi: Double): Double {
        return a / sqrt(1.0 - ecc2 * sin(phi).pow(2))
    }

    // https://www.geodetic.gov.hk/common/data/pdf/explanatorynotes.pdf
    fun meridianRadiusOfCurvature(phi: Double): Double {
        return a * (1 - ecc2) / (1 - ecc2 * sin(phi).pow(2)).pow(1.5)
    }
}