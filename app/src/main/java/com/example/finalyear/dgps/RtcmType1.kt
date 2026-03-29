/**
 * Algorithm adapted from:
 * Federal Agency for Cartography and Geodesy (BKG) (2008) RTCM2.cpp. [source code]
 * Available at: <https://software.rtcm-ntrip.org/browser/ntrip/trunk/BNC/RTCM/RTCM2.cpp?rev=1044>
 *
 * Transmission Reference:
 * Radio Technical Commission for Maritime Services (1998) RTCM Recommended Standards for Differential GNSS (Global Navigation Satellite Systems) Service, Version 2.2. Alexandria, VA: RTCM.
 * Betke, K. (2001). Transmission Characteristics of Marine Differential GPS (DGPS) Stations.
 * Available at: <https://www.sigidwiki.com/images/6/66/Rtcm-sc104-transmission-characteristics-of-marine-differential-gps-stations.pdf>.
 */
package com.example.finalyear.dgps

import com.example.finalyear.util.BitArray

class RtcmType1 {
    companion object {
        // By now we have stripped all parity bits (becomes sequence of 40 bits)
        fun decode(rtcm: Rtcm) {
            // RTCM v2 type 1: differential GPS correction (GPS only)
            var i = 48  // start of type-1 body (after 48-bit header)
            while (i + 40 <= rtcm.len * 8) {
                val bitArray = BitArray(rtcm.buff)

                val fact = bitArray.getUnsigned(i, 1)
                i += 1
                val udre = bitArray.getUnsigned(i, 2)
                i += 2
                var prn = bitArray.getUnsigned(i, 5)
                i += 5
                val prc = bitArray.getSigned(i, 16)
                i += 16
                val rrc = bitArray.getSigned(i, 8)
                i += 8
                val iod = bitArray.getUnsigned(i, 8)
                i += 8

                // In 5-bit representation, prn=32 can only be represented as 0
                if (prn == 0) {
                    prn = 32
                }

                // Satellite problem indicators (match RTKLIB behavior/intent)
                if (prc == -32768 || rrc == -128) {
                    continue
                }

                val sat = Rtcm.satNo(SYS_GPS, prn) ?: continue

                val scalePrc = if (fact != 0) 0.32 else 0.02
                val scaleRrc = if (fact != 0) 0.032 else 0.002

                // Store DGPS corrections
                rtcm.dgps[sat - 1] = DgpsCorrection(
                    prc = prc.toDouble() * scalePrc,
                    rrc = rrc.toDouble() * scaleRrc,
                    iod = iod,
                    udre = udre
                )
            }
        }
    }
}