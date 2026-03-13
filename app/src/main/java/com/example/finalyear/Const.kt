@file:Suppress("ConstPropertyName", "unused")

package com.example.finalyear

object Const {
    const val c = 299792458.0      // Speed of light (m/s)
    const val g = 9.80665          // Acceleration of earth's gravity (m/s^2)
    const val G = 6.67259e-11      // Universal gravitational constant (m^3/kg s^2)
    const val M = 5.972e24         // Mass of the Earth (kg)
    const val Mu = 3.986004418e14  // Standard gravitational parameter (μ = G * M) (m^3/s^2)
    const val OmegaE = 7.2921151467e-5  // Earth rotation rate (rad/s)
    const val RelativisticF = -4.442807633e-10

    const val HALF_WEEK = 3.5 * 60 * 60 * 24
    const val NS_PER_WEEK = 604_800.0 * 1e9

    // Tropospheric correction
    const val B_HYDROSTATIC = 0.0035716
    const val C_HYDROSTATIC = 0.082456
    const val B_NON_HYDROSTATIC = 0.0018576
    const val C_NON_HYDROSTATIC = 0.062741
}