package com.example.finalyear.util

import kotlin.math.floor

val leaps = listOf(
    intArrayOf(2017, 1, 1, 0, 0, 0, -18),
    intArrayOf(2015, 7, 1, 0, 0, 0, -17),
    intArrayOf(2012, 7, 1, 0, 0, 0, -16),
    intArrayOf(2009, 1, 1, 0, 0, 0, -15),
    intArrayOf(2006, 1, 1, 0, 0, 0, -14),
    intArrayOf(1999, 1, 1, 0, 0, 0, -13),
    intArrayOf(1997, 7, 1, 0, 0, 0, -12),
    intArrayOf(1996, 1, 1, 0, 0, 0, -11),
    intArrayOf(1994, 7, 1, 0, 0, 0, -10),
    intArrayOf(1993, 7, 1, 0, 0, 0, -9),
    intArrayOf(1992, 7, 1, 0, 0, 0, -8),
    intArrayOf(1991, 1, 1, 0, 0, 0, -7),
    intArrayOf(1990, 1, 1, 0, 0, 0, -6),
    intArrayOf(1988, 1, 1, 0, 0, 0, -5),
    intArrayOf(1985, 7, 1, 0, 0, 0, -4),
    intArrayOf(1983, 7, 1, 0, 0, 0, -3),
    intArrayOf(1982, 7, 1, 0, 0, 0, -2),
    intArrayOf(1981, 7, 1, 0, 0, 0, -1),
    intArrayOf(0)
)

data class GTime(val time: Long = 0L, val sec: Double = 0.0) {
    companion object {
        fun fromWeekAndTow(week: Int, tow: Double): GTime {
            val gpst0 = listOf(1980, 1, 6, 0, 0, 0)
            val t0 = fromEpoch(gpst0)
            val secTotal = week * 86400 * 7 + tow
            val carry = floor(secTotal).toLong()
            var tTime = t0.time + carry
            var tSec = secTotal - carry + t0.sec
            val carrySec = floor(tSec).toLong()
            tTime += carrySec
            tSec -= carrySec
            return GTime(tTime, tSec)
        }

        fun fromEpoch(ep: List<Number>): GTime {
            val doy = intArrayOf(1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)
            val year = ep[0].toInt()
            val mon = ep[1].toInt()
            val day = ep[2].toInt()
            val h = ep[3].toInt()
            val m = ep[4].toInt()
            val s = ep[5].toDouble()
            if (year < 1970 || year > 2099 || mon < 1 || mon > 12) {
                return GTime()
            }
            val leap = if (year % 4 == 0 && mon >= 3) 1 else 0
            val days = (year - 1970) * 365 + (year - 1969) / 4 + doy[mon - 1] + day - 2 + leap
            val secInt = floor(s).toInt()
            val unixTime = days * 86400L + h * 3600L + m * 60L + secInt
            val frac = s - secInt
            return GTime(unixTime, frac)
        }

        fun fromUnixNow(): GTime {
            val unix = System.currentTimeMillis() / 1000.0
            val tTime = unix.toLong()
            val tSec = unix - tTime
            return GTime(tTime, tSec).utcToGpst()
        }

        // Returns null on parse failure
        fun deserialize(str: String): GTime? {
            // Remove surrounding parentheses and whitespace
            val clean = str.trim().removeSurrounding("(", ")").trim()

            // Split by comma (but allow spaces around)
            val parts = clean.split(",").map { it.trim() }
            if (parts.size != 2) return null

            val timeStr = parts[0]
            val secStr = parts[1]

            return try {
                val t = timeStr.toLongOrNull() ?: return null
                val s = secStr.toDoubleOrNull() ?: return null
                GTime(t, s)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun timeAdd(secs: Double): GTime {
        val tt = sec + secs
        val carry = floor(tt).toLong()
        val newTime = time + carry
        val newSec = tt - carry
        return GTime(newTime, newSec)
    }

    fun timeDiff(other: GTime): Double {
        return (time - other.time).toDouble() + (sec - other.sec)
    }

    /** Convert common UTC time to GPS time (adjusting for leap seconds) **/
    fun utcToGpst(): GTime {
        for (leap in leaps) {
            if (leap[0] == 0) {
                break
            }
            val tl = fromEpoch(leap.toList())
            if (this.timeDiff(tl) >= 0.0) {
                return this.timeAdd(-leap[6].toDouble())
            }
        }
        return this
    }

    fun asGpsTimeSecs(): Double {
        val gpst0 = listOf(1980, 1, 6, 0, 0, 0)
        val t0 = fromEpoch(gpst0)
        return (time - t0.time).toDouble() + (sec - t0.sec)
    }

    fun asWeekAndTow(): Pair<Int, Double> {
        val secTotal = asGpsTimeSecs()
        val week = floor(secTotal / (86400 * 7)).toInt()
        val tow = secTotal - week * 86400 * 7
        return Pair(week, tow)
    }

    fun serialize(): String {
        return "($time, $sec)"
    }
}