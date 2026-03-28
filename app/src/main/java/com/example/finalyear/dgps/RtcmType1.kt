package com.example.finalyear.dgps

import com.example.finalyear.util.BitArray

class RtcmType1 {
    companion object {
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

                // Store DGPS corrections
                val scalePrc = if (fact != 0) 0.32 else 0.02
                val scaleRrc = if (fact != 0) 0.032 else 0.002

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