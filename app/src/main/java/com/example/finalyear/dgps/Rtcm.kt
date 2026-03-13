package com.example.finalyear.dgps

import android.util.Log
import com.example.finalyear.util.GTime
import kotlin.math.floor

const val SYS_GPS = 0x01
const val NUM_SATS = 32

data class Rtcm (
    var time: GTime = GTime(),
    var recvTime: GTime = GTime(),
    var buff: ByteArray = byteArrayOf(),
    var len: Int = 0,
    var msgType: Int = 0,
    var dgps: MutableList<DgpsCorrection?> = MutableList(NUM_SATS) { null },  // A bucket for all GPS satellites
    var stationId: Int = 0,
    var seqNo: Int = 0,
    var stationHealth: Int = 0,
) {
    init {
        time = GTime()
        buff = ByteArray(1200)
        dgps = MutableList(NUM_SATS) { null }
    }
    companion object {
        // Verifies the satellite comes from GPS and in valid range
        fun satNo(sys: Int, prn: Int): Int? {
            if (sys != SYS_GPS) {
                return null
            }
            if (prn < 1 || prn > 32) {
                return null
            }
            return prn
        }

        fun deserialize(text: String): Rtcm? {
            val rtcm = Rtcm()

            val chars = text.codePoints().mapToObj { charInt -> charInt.toChar() }.iterator()
            val keyBuff = StringBuilder()
            val valueBuff = StringBuilder()

            return try {
                while (chars.hasNext()) {
                    // Push key
                    while (chars.hasNext()) {
                        val char = chars.next()
                        if (char == ':') break
                        keyBuff.append(char)
                    }
                    val key = keyBuff.toString().trim()
                    keyBuff.clear()

                    // Push value
                    val terminator = if (key == "buff") '◉' else '\n'
                    while (chars.hasNext()) {
                        val char = chars.next()
                        if (char == terminator) break
                        valueBuff.append(char)
                    }
                    val rawValue = valueBuff.toString().trim()
                    valueBuff.clear()

                    // Consume newline
                    if (key == "buff") {
                        do {
                            val char = chars.next()
                        } while (char != '\n')
                    }

                    // Handle value
                    when (key) {
                        "time" -> {
                            GTime.deserialize(rawValue)?.let { rtcm.time = it }
                                ?: return null
                        }
                        "recvTime" -> {
                            GTime.deserialize(rawValue)?.let { rtcm.recvTime = it }
                                ?: return null
                        }
                        "buff" -> {
                            val bytesInUtf8 = valueBuff.toString()           // content before ◉
                            val bytes = bytesInUtf8.toByteArray()
                            rtcm.buff = bytes
                        }

                        "len" -> {
                            rtcm.len = rawValue.toIntOrNull() ?: return null
                        }

                        "msgType" -> {
                            rtcm.msgType = rawValue.toIntOrNull() ?: return null
                        }

                        "dgps" -> {
                            val content = rawValue.trim()

                            if (content == "[]") {
                                // empty list is allowed
                                continue
                            }

                            val chars = content.codePoints()
                                .mapToObj { it.toChar() }
                                .iterator()
                            // Should start with [
                            if (chars.next() != '[') return null

                            var index = 0

                            while (chars.hasNext()) {
                                val char = chars.next()
                                if (!char.isWhitespace()) {
                                    if (char == ',') continue
                                    if (char == ']') break  // End of list reached

                                    val itemBuilder = StringBuilder()
                                    itemBuilder.append(char)

                                    if (index >= rtcm.dgps.size) return null

                                    if (char == 'n') {
                                        // expect "null"
                                        var nullStr = "n"
                                        while (chars.hasNext() && nullStr.length < 4) {
                                            nullStr += chars.next()
                                        }
                                        if (nullStr != "null") return null
                                        rtcm.dgps[index] = null
                                    } else if (char == '(') {
                                        var depth = 1
                                        while (chars.hasNext()) {
                                            val next = chars.next()
                                            itemBuilder.append(next)
                                            when (next) {
                                                '(' -> depth++
                                                ')' -> {
                                                    depth--
                                                    if (depth == 0) break
                                                }
                                            }
                                        }
                                        if (depth != 0) return null  // unbalanced parentheses

                                        val itemStr = itemBuilder.toString()
                                        val correction =
                                            DgpsCorrection.deserialize(itemStr) ?: return null
                                        rtcm.dgps[index] = correction
                                    } else {
                                        // unexpected start character
                                        return null
                                    }

                                    index++
                                }
                            }

                            // If iterator still has content → too many items or trailing junk
                            if (chars.hasNext()) return null
                        }

                        "stationId" -> {
                            rtcm.stationId = rawValue.toIntOrNull() ?: return null
                        }

                        "seqNo" -> {
                            rtcm.seqNo = rawValue.toIntOrNull() ?: return null
                        }

                        "stationHealth" -> {
                            rtcm.stationHealth = rawValue.toIntOrNull() ?: return null
                        }

                        else -> {
                            // ignore unknown keys (or return null if you prefer strict parsing)
                        }
                    }
                }
                rtcm
            } catch (e: Exception) {
                null
            }
        }
    }

    fun adjHour(zCount: Double) {
        if (time.time == 0L && time.sec == 0.0) {
            time = GTime.fromUnixNow()
        }
        val (week, tow) = time.asWeekAndTow()
        val hour = floor(tow / 3600).toInt()
        val sec = tow - hour * 3600
        var adjustedZCount = zCount
        if (adjustedZCount < sec - 1800) {
            adjustedZCount += 3600
        } else if (adjustedZCount > sec + 1800) {
            adjustedZCount -= 3600
        }
        time = GTime.fromWeekAndTow(week, (hour * 3600 + adjustedZCount))
    }

    // Returns null if message is not DGPS (Type 1) or no record found for PRN
    fun weightOfSat(prn: Int, time: Double): Double? {
        if (msgType != 1) {
            return null
        }
        val dgpsCorrection = dgps[prn - 1] ?: return null
        // Using a heuristic value: sigma = 2^udre
        val sigmaUdre = 2u shl dgpsCorrection.udre
        val varianceUdre = (sigmaUdre * sigmaUdre).toDouble()

        val deltaT = time - dgpsCorrection.t0.asGpsTimeSecs()

        // Variance = σ_udre ^ 2 + health * (Δt) ^ 2
        // station health the lower the better
        val variance = varianceUdre + deltaT * deltaT * stationHealth

        // Weight = 1 / σ^2
        return 1 / variance
    }

    fun serialize(): String {
        val sb = StringBuilder()

        sb.append("time: ").append(time.serialize()).append("\n")
        sb.append("recvTime: ").append(recvTime.serialize()).append("\n")

        sb.append("buff: ")
        sb.append(String(buff, 0, buff.size, Charsets.UTF_8))
        sb.append("◉\n")

        sb.append("len: ").append(len).append("\n")
        sb.append("msgType: ").append(msgType).append("\n")

        sb.append("dgps: [")
        dgps.forEachIndexed { index, corr ->
            if (index > 0) sb.append(", ")
            if (corr == null) sb.append("null")
            else sb.append(corr.serialize())
        }
        sb.append("]\n")

        sb.append("stationId: ").append(stationId).append("\n")
        sb.append("seqNo: ").append(seqNo).append("\n")
        sb.append("stationHealth: ").append(stationHealth).append("\n")

        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Rtcm

        if (time != other.time) return false
        if (!buff.contentEquals(other.buff)) return false
        if (len != other.len) return false
        if (msgType != other.msgType) return false
        if (dgps != other.dgps) return false
        if (stationId != other.stationId) return false
        if (seqNo != other.seqNo) return false
        if (stationHealth != other.stationHealth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + buff.contentHashCode()
        result = 31 * result + len
        result = 31 * result + msgType
        result = 31 * result + dgps.hashCode()
        result = 31 * result + stationId
        result = 31 * result + seqNo
        result = 31 * result + stationHealth
        return result
    }
}