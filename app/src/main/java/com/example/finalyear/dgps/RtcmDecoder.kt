package com.example.finalyear.dgps

import android.util.Log
import com.example.finalyear.util.BitArray

const val RTCM2_PREAMBLE = 0x66

class RtcmDecoder {

    companion object {
        fun decodeRtcm(rawMsg: ByteArray): Rtcm? {
            val rtcm = Rtcm()
            rtcm.resizeBuffers()

            var word = 0u
            var nbyte = 0
            var nbit = 0
            rtcm.len = 0
            val buff = ByteArray(1200)

            var parityOk1 = false
            var parityOk2 = false

            for (byte in rawMsg) {
                var data = byte.toInt() and 0xFF
                if ((data and 0xC0) != 0x40) {
                    continue
                }
                for (_i in 0 until 6) {
                    word = (word shl 1) and 0xFFFFFFFFu
                    word += (data and 1).toUInt()
                    data = data shr 1

                    // Synchronize frame
                    if (nbyte == 0) {
                        var preamble = ((word shr 22) and 0xFFu).toInt()
                        if (word and 0x40000000u != 0u) {
                            preamble = preamble xor 0xFF
                        }
                        if (preamble != RTCM2_PREAMBLE) {
                            continue
                        }

                        // Check parity (word1)
                        val dataOut = ByteArray(3)
                        parityOk1 = RtcmParity.check(word.toInt(), dataOut)
                        if (!parityOk1) {
                            continue
                        }

                        dataOut.copyInto(buff, 0, 0, 3)
                        nbyte = 3
                        nbit = 0
                        rtcm.len = 0

                        continue
                    }

                    nbit += 1
                    if (nbit < 30) {
                        continue
                    }
                    nbit = 0

                    // Check parity (subsequent words, including word2)
                    val dataOut = ByteArray(3)
                    parityOk2 = RtcmParity.check(word.toInt(), dataOut)
                    if (!parityOk2) {
                        nbyte = 0
                        rtcm.len = 0
                        word = word and 0x3u
                        continue
                    }

                    dataOut.copyInto(buff, nbyte, 0, 3)
                    nbyte += 3

                    if (nbyte == 6) {
                        rtcm.len = ((buff[5].toInt() and 0xFF) shr 3) * 3 + 6
                    }

                    // Wait until whole message collected
                    if (rtcm.len == 0 || nbyte < rtcm.len) {
                        continue
                    }

                    // Decode Rtcm2 header fields
                    val bits = BitArray(buff)
                    val preamble = bits.getUnsigned(0, 8)
                    val messageType = bits.getUnsigned(8, 6)
                    val stationId = bits.getUnsigned(14, 10)
                    val zCount = bits.getUnsigned(24, 13) * 0.6
                    val seqNumber = bits.getUnsigned(37, 3)
                    val frameLength = bits.getUnsigned(40, 5)
                    val stationHealth = bits.getUnsigned(45, 3)

                    // Valid time the nadjust hour rollover
                    if (zCount >= 3600.0) {
                        nbyte = 0
                        rtcm.len = 0
                        word = word and 0x3u
                        continue
                    }

                    rtcm.adjHour(zCount)
                    buff.copyInto(rtcm.buff, 0, 0, rtcm.len)  // Store the trimmed, actual message bytes
                    rtcm.msgType = messageType
                    rtcm.seqNo = seqNumber
                    rtcm.stationHealth = stationHealth
                    if (rtcm.stationId == 0) {
                        rtcm.stationId = stationId
                    }

                    if (messageType == 1) {
                        RtcmType1.decode(rtcm)
                        return rtcm  // Return known RTCM types
                    } else {
                        Log.w("GNSS", "Received RTCM message with unhandled type: $messageType from station $stationId")
                    }

                    // Reset for next message (like RTKLIB)
                    nbyte = 0
                    rtcm.len = 0
                    word = word and 0x3u
                }
            }

            return null  // No valid bytes or unknown RTCM types
        }
    }
}