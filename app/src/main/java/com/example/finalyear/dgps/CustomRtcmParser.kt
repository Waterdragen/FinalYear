package com.example.finalyear.dgps

import android.util.Log

class CustomRtcmParser {
    companion object {

        fun serializeStationRtcmMap(map: Map<Int, Rtcm>): String {
            val sb = StringBuilder()

            map.forEach { (stationId, rtcm) ->
                sb.append("stationId: ").append(stationId).append("☆\n")
                sb.append(rtcm.serialize())
                sb.append("☆\n")
            }

            return sb.toString()
        }

        fun deserializeStationRtcmMap(text: String): Map<Int, Rtcm>? {
            if (text.isEmpty()) return null

            val map = mutableMapOf<Int, Rtcm>()

            val chars = text.codePoints().mapToObj { it.toChar() }.iterator()
            val keyBuff = StringBuilder()
            val valueBuff = StringBuilder()

            try {
                while (chars.hasNext()) {
                    // Handle key
                    while (chars.hasNext()) {
                        val char = chars.next()
                        if (char == '☆') break
                        keyBuff.append(char)
                    }
                    val stationIdLine = keyBuff.toString().trim()
                    keyBuff.clear()
                    if (!stationIdLine.startsWith("stationId:")) return null

                    val stationIdStr = stationIdLine.substringAfter("stationId:").trim()
                    val stationId = stationIdStr.toIntOrNull() ?: return null

                    // Handle value
                    while (chars.hasNext()) {
                        val char = chars.next()
                        if (char == '☆') break
                        valueBuff.append(char)
                    }
                    val rtcmText = valueBuff.toString()
                    valueBuff.clear()
                    val rtcm = Rtcm.deserialize(rtcmText) ?: return null
                    map[stationId] = rtcm

                    if (chars.hasNext()) {
                        val newLineAfterStar = chars.next()
                        if (newLineAfterStar != '\n') return null
                    }
                }
            } catch (e: Exception) {
                Log.e("GNSS", e.stackTraceToString())
                return null
            }
            return map
        }
    }
}