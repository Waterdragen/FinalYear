@file:Suppress("ConstPropertyName", "unused")

package com.example.finalyear.math

object Const {
    const val c = 299792458.0      // Speed of light (m/s)
    const val g = 9.80665          // Acceleration of earth's gravity (m/s^2)
    const val G = 6.67259e-11      // Universal gravitational constant (m^3/kg s^2)
    const val M = 5.972e24         // Mass of the Earth (kg)
    const val Mu = 3.986004418e14  // Standard gravitational parameter (μ = G * M) (m^3/s^2)
    const val OmegaE = 7.2921151467e-5  // Earth rotation rate (rad/s)
    const val RelativisticF = -4.442807633e-10

    const val HALF_WEEK_SEC = 302400.0
    const val WEEK_SEC = 604800.0
    const val WEEK_NS = 604800_000_000_000L
    const val HALF_WEEK_NS = 302400_000_000_000L

    const val L1_FREQ_HZ = 1.57542e9

    // Tropospheric correction
    const val B_HYDROSTATIC = 0.0035716
    const val C_HYDROSTATIC = 0.082456
    const val B_NON_HYDROSTATIC = 0.0018576
    const val C_NON_HYDROSTATIC = 0.062741

    // Van Dierendonck (1996) code tracking jitter model
    const val GPS_BANDWIDTH = 2.0                    // B_L (Hz)
    const val GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1  // d
    const val GPS_DLL_AVERAGING_TIME_SEC = 20e-3     // T (ms)

    const val ANTENNA_HEIGHT_M = 1.0
}