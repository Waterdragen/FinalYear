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

    // UNBabc mapping function (Tropospheric)
    // Guo, J. & Langley, R.B. (2003) 'A New Tropospheric Propagation Delay Mapping Function for Elevation Angles Down to 2°', in Proceedings of the 16th International Technical Meeting of the Satellite Division of The Institute of Navigation (ION GPS/GNSS 2003), Portland, OR, 9-12 September. Manassas, VA: The Institute of Navigation, pp. 376-386.
    const val B_HYDROSTATIC = 0.0035716
    const val C_HYDROSTATIC = 0.082456
    const val B_NON_HYDROSTATIC = 0.0018576
    const val C_NON_HYDROSTATIC = 0.062741

    // Van Dierendonck code tracking jitter model (pseudorange)
    // Van Dierendonck A. J. (1996) ‘GPS Receivers’. Global Positioning System: theory and applications. Cap 8. Vol 1, AIAA Progress in Astronautics and Aeronautics. doi: 10.2514/5.9781600866388.0329.0407
    const val GPS_BANDWIDTH = 2.0                    // B_L (Hz)
    const val GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1  // d
    const val GPS_DLL_AVERAGING_TIME_SEC = 20e-3     // T (ms)

    const val ANTENNA_HEIGHT_M = 1.0  // The surveyor holds the phone roughly 1m above the benchmark
}