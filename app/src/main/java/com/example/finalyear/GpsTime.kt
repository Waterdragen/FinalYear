package com.example.finalyear

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GpsTime private constructor(val nanos: Long): Comparable<GpsTime> {
    companion object {
        const val MILLIS_IN_SECOND: Int = 1000
        const val SECONDS_IN_MINUTE: Int = 60
        const val MINUTES_IN_HOUR: Int = 60
        const val HOURS_IN_DAY: Int = 24
        const val SECONDS_IN_DAY: Int = HOURS_IN_DAY * MINUTES_IN_HOUR * SECONDS_IN_MINUTE
        const val DAYS_IN_WEEK: Int = 7
        val MILLIS_IN_DAY: Long = TimeUnit.DAYS.toMillis(1)
        val MILLIS_IN_WEEK: Long = TimeUnit.DAYS.toMillis(7)
        val NANOS_IN_WEEK: Long = TimeUnit.DAYS.toNanos(7)

        // GPS epoch is 1980/01/06
        const val GPS_DAYS_SINCE_JAVA_EPOCH: Long = 3657
        val GPS_UTC_EPOCH_OFFSET_SECONDS: Long = TimeUnit.DAYS.toSeconds(GPS_DAYS_SINCE_JAVA_EPOCH)
        val GPS_UTC_EPOCH_OFFSET_NANOS: Long =
            TimeUnit.SECONDS.toNanos(GPS_UTC_EPOCH_OFFSET_SECONDS)
        private val UTC_ZONE: DateTimeZone = DateTimeZone.UTC
        private val LEAP_SECOND_DATE_1981: DateTime = DateTime(1981, 7, 1, 0, 0, UTC_ZONE)
        private val LEAP_SECOND_DATE_2012: DateTime = DateTime(2012, 7, 1, 0, 0, UTC_ZONE)
        private val LEAP_SECOND_DATE_2015: DateTime = DateTime(2015, 7, 1, 0, 0, UTC_ZONE)
        private val LEAP_SECOND_DATE_2017: DateTime = DateTime(2017, 1, 1, 0, 0, UTC_ZONE)

        fun fromNanos(nanos: Long): GpsTime {
            return GpsTime(nanos)
        }
        fun tryFromString(s: String): GpsTime? {
            val nanos = s.toLongOrNull() ?: return null
            return this.fromNanos(nanos)
        }
        fun fromDateFloatSec(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Double): GpsTime {
            val intSec = second.toInt()
            val intMillis = (second * 1000.0 % 1000.0).toInt()
            val utcDateTime = DateTime(year, month, day, hour, minute, intSec, intMillis)
            val nanos = TimeUnit.MILLISECONDS.toNanos(utcDateTime.millis) - GPS_UTC_EPOCH_OFFSET_NANOS
            return this.fromNanos(nanos)
        }
        fun fromDateTime(dateTime: DateTime): GpsTime {
            val nanos = (TimeUnit.MILLISECONDS.toNanos(dateTime.millis) +
                         TimeUnit.SECONDS.toNanos(leapSecond(dateTime) - GPS_UTC_EPOCH_OFFSET_SECONDS))
            return this.fromNanos(nanos)
        }
        fun fromNow(): GpsTime {
            return this.fromDateTime(DateTime.now(DateTimeZone.UTC))
        }
        fun fromWeekTow(gpsWeek: Int, towSec: Int): GpsTime {
            val nanos = gpsWeek.toLong() * NANOS_IN_WEEK + TimeUnit.SECONDS.toNanos(towSec.toLong())
            return this.fromNanos(nanos)
        }
        fun fromSec(sec: Int): GpsTime {
            return this.fromNanos(TimeUnit.SECONDS.toNanos(sec.toLong()))
        }
        fun leapSecond(dateTime: DateTime): Int {
            if (LEAP_SECOND_DATE_2017 <= dateTime) return 18
            if (LEAP_SECOND_DATE_2015 <= dateTime) return 17
            if (LEAP_SECOND_DATE_2012 <= dateTime) return 16
            if (LEAP_SECOND_DATE_1981 <= dateTime) return 15
            return 0
        }
    }

    // Whole week duration (ns) since GPS epoch
    fun gpsWeekInNs(): Long {
        return gpsWeek() * NANOS_IN_WEEK
    }
    // Week count since GPS epoch
    fun gpsWeek(): Int {
        return (nanos / NANOS_IN_WEEK).toInt()
    }
    // Seconds since start of week
    fun gpsTowSec(): Int {
        return TimeUnit.NANOSECONDS.toSeconds(nanos % NANOS_IN_WEEK).toInt()
    }
    fun timeInCalendar(): Calendar {
        return toRawDateTime().toGregorianCalendar()
    }
    // Convert to DateTime with leap seconds
    fun toDateTime(): DateTime {
        val gpsDateTime: DateTime = toRawDateTime()
        return DateTime(
            gpsDateTime.millis - TimeUnit.SECONDS.toMillis(leapSecond(gpsDateTime).toLong()), UTC_ZONE
        )
    }
    // Convert to DateTime (without leap seconds)
    fun toRawDateTime(): DateTime {
        return DateTime(
            TimeUnit.NANOSECONDS.toMillis(nanos + GPS_UTC_EPOCH_OFFSET_NANOS),
            UTC_ZONE
        )
    }

    // GPS weekly epoch of the referennce time
    fun gpsWeekEpochNano(): Long {
        val weekSecond = this.getGpsWeekSecond()
        return weekSecond.first * NANOS_IN_WEEK
    }

    // Week count since GPS epoch, and second count since beginning of that week
    fun getGpsWeekSecond(): Pair<Int, Int> {
        val week = (nanos / NANOS_IN_WEEK).toInt()
        val second = TimeUnit.NANOSECONDS.toSeconds(nanos % NANOS_IN_WEEK).toInt()
        return Pair(week, second)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GpsTime) return false
        return this.nanos == other.nanos
    }

    override fun compareTo(other: GpsTime): Int {
        return this.nanos.compareTo(other.nanos)
    }

    override fun toString(): String {
        return nanos.toString()
    }

    override fun hashCode(): Int {
        return nanos.hashCode()
    }
}