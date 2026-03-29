/**
 * Algorithm adapted from:
 * Federal Agency for Cartography and Geodesy (BKG) (2008) RTCM2.cpp.
 * Available at: <https://software.rtcm-ntrip.org/browser/ntrip/trunk/BNC/RTCM/RTCM2.cpp?rev=1044> [Accessed: 25 January 2026].
 *
 * Transmission Reference:
 * Radio Technical Commission for Maritime Services (1998) RTCM Recommended Standards for Differential GNSS (Global Navigation Satellite Systems) Service, Version 2.2. Alexandria, VA: RTCM.
 * Betke, K. (2001). Transmission Characteristics of Marine Differential GPS (DGPS) Stations.
 * Available at: <https://www.sigidwiki.com/images/6/66/Rtcm-sc104-transmission-characteristics-of-marine-differential-gps-stations.pdf>. [Accessed 28 March 2026].
 */
package com.example.finalyear.dgps

class RtcmParity {
    companion object {
        fun check(originalWord: Int, data: ByteArray): Boolean {
            val parity = computeExpected(originalWord)
            if (parity != (originalWord and 0x3F)) {
                return false
            }
            val word = retainOrInvertData(originalWord)
            for (i in 0 until 3) {
                // Only keep 24 bits raw data, storing as 3 bytes
                // Byte 1: [Top 2 bits] [8 bits DATA] [8 + 8 + 6 shifts]
                // Byte 2: [Top 2 bits] [8] [8 bits DATA] [8 + 6 shifts]
                // Byte 3: [Top 2 bits] [8] [8] [8 bits DATA] [6 shifts]
                // byte & 0xFF removes the top 2 bits
                data[i] = ((word ushr (22 - i * 8)) and 0xFF).toByte()
            }
            return true
        }

        fun computeExpected(originalWord: Int): Int {
            val hamming = intArrayOf(0xBB1F3480.toInt(), 0x5D8F9A40, 0xAEC7CD00.toInt(), 0x5763E680, 0x6BB1F340, 0x8B7A89C0.toInt())
            val word = retainOrInvertData(originalWord)
            var parity = 0
            for (i in 0 until 6) {
                parity = parity shl 1
                val w = (word and hamming[i]) ushr 6  // 24 bits data after hamming
                parity = parity xor (Integer.bitCount(w) % 2)
            }
            return parity
        }

        private fun retainOrInvertData(word: Int): Int {
            // The top 2 bits are not 0x00, indicating an inversion
            if ((word and 0x40000000) != 0) {
                // Top 2 bits xor 0, first 24 bits (data) xor 1, last 6 bits (parity) xor 0
                // Xor 1 flips bits, xor 0 retains bits
                return word xor 0x3FFFFFC0
            }
            // Retains all bits
            return word
        }
    }
}