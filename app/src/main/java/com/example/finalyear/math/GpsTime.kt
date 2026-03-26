package com.example.finalyear.math

import kotlinx.serialization.Serializable
import org.joda.time.DateTime

// Does not account for leap seconds
@Serializable
@JvmInline
value class GpsTime(val nanos: Long) {
    companion object {
        fun fromWeekAndTowSec(weekNum: Double, towSec: Double): GpsTime {
            val nanos = (weekNum * Const.WEEK_SEC + towSec) * 1e9
            return GpsTime(nanos.toLong())
        }

        fun fromDatetime(dateTime: DateTime): GpsTime {
            val nsSinceUnixEpoch: Long = dateTime.millis * 1_000_000L
            val nsSinceGpsEpoch: Long = nsSinceUnixEpoch - 315_964_800_000_000_000L
            return GpsTime(nsSinceGpsEpoch)
        }

        fun fromUnixNow(): GpsTime {
            val msSinceUnixEpoch = System.currentTimeMillis()
            val dateTime = DateTime(msSinceUnixEpoch)
            return fromDatetime(dateTime)
        }
    }

    val secs: Double get() = nanos.toDouble() * 1e-9
    val weekNum: Long get() = nanos / Const.WEEK_NS
    val towNs: Long get() = nanos.mod(Const.WEEK_NS)

    fun toDatetime(): DateTime {
        val nsSinceUnixEpoch = nanos + 315_964_800_000_000_000L
        val msSinceUnixEpoch = nsSinceUnixEpoch / 1_000_000L
        return DateTime(msSinceUnixEpoch)
    }

    fun asWeekAndTowSec(): Pair<Double, Double> {
        return weekNum.toDouble() to towNs.toDouble() * 1e-9
    }
}