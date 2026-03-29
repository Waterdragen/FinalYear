/**
 * Algorithm adapted from:
 * Google (2023) GNSSLogger.
 * Available from https://github.com/google/gps-measurement-tools/tree/master/GNSSLogger
 *
 * Specifications for the bit representations and scale factors:
 * US Department of Defense (1995) Global Positioning System Standard Positioning Service Signal Specification
 * Available from https://www.gps.gov/sites/default/files/2025-07/1995-SPS-signal-specification.pdf
 */

package com.example.finalyear.util

import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.util.Log
import com.example.finalyear.PageSurvey
import com.example.finalyear.core.NavData
import com.example.finalyear.model.Ionospheric
import com.example.finalyear.core.ObsData
import com.example.finalyear.core.PartialNavData
import com.example.finalyear.math.GpsTime
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class Parser {
    data class BytePos(val index: Int, val length: Int)

    class GpsEphemerisDecoder {
        companion object {
            const val IONOSPHERIC_PARAMETERS_PAGE_18_SV_ID = 56

            const val WORD_SIZE_BITS = 30
            const val WORD_PADDING_BITS = 2
            const val GPS_CYCLE_WEEKS = 1024
            const val IODE_TO_IODC_MASK = 0xFF
            const val L1_CA_MESSAGE_LENGTH_BYTES = 40

            val POW_2_4 = 2.0.pow(4.0)
            val POW_2_11 = 2.0.pow(11.0)
            val POW_2_14 = 2.0.pow(14.0)
            val POW_2_16 = 2.0.pow(16.0)

            val POW_2_NEG_5 = 2.0.pow(-5.0)
            val POW_2_NEG_19 = 2.0.pow(-19.0)
            val POW_2_NEG_24 = 2.0.pow(-24.0)
            val POW_2_NEG_27 = 2.0.pow(-27.0)
            val POW_2_NEG_29 = 2.0.pow(-29.0)
            val POW_2_NEG_30 = 2.0.pow(-30.0)
            val POW_2_NEG_31 = 2.0.pow(-31.0)
            val POW_2_NEG_33 = 2.0.pow(-33.0)
            val POW_2_NEG_43 = 2.0.pow(-43.0)
            val POW_2_NEG_55 = 2.0.pow(-55.0)

            const val INTEGER_RANGE = -1  // All ones in bits
            // A GPS Cycle is 1024 weeks, or 7168 days
            val GPS_CYCLE_SECS = TimeUnit.DAYS.toSeconds(7168)

            val IODC1_POS = BytePos(82, 2)
            val IODC2_POS = BytePos(210, 8)
            val WEEK_POS = BytePos(60, 10)
            val URA_POS = BytePos(72, 4)
            val SV_HEALTH_POS = BytePos(76, 6)
            val TGD_POS = BytePos(196, 8)
            val AF2_POS = BytePos(240, 8)
            val AF1_POS = BytePos(248, 16)
            val AF0_POS = BytePos(270, 22)
            val IODE1_POS = BytePos(60, 8)
            val IODE2_POS = BytePos(270, 8)
            val TOC_POS = BytePos(218, 16)
            val CRS_POS = BytePos(68, 16)
            val DELTA_N_POS = BytePos(90, 16)
            const val M0_INDEX8 = 106
            const val M0_INDEX24 = 120
            val CUC_POS = BytePos(150, 16)
            const val E_INDEX8 = 166
            const val E_INDEX24 = 180
            val CUS_POS = BytePos(210, 16)
            const val A_INDEX8 = 226
            const val A_INDEX24 = 240
            val TOE_POS = BytePos(270, 16)
            val CIC_POS = BytePos(60, 16)
            const val O0_INDEX8 = 76
            const val O0_INDEX24 = 90
            const val O_INDEX8 = 196
            const val O_INDEX24 = 210
            val ODOT_POS = BytePos(240, 24)
            val CIS_POS = BytePos(120, 16)
            const val I0_INDEX8 = 136
            const val I0_INDEX24 = 150
            val CRC_POS = BytePos(180, 16)
            val IDOT_POS = BytePos(278, 14)
            val A0_POS = BytePos(68, 8)
            val A1_POS = BytePos(76, 8)
            val A2_POS = BytePos(90, 8)
            val A3_POS = BytePos(98, 8)
            val B0_POS = BytePos(106, 8)
            val B1_POS = BytePos(120, 8)
            val B2_POS = BytePos(128, 8)
            val B3_POS = BytePos(136, 8)
        }

        // The prn range for GPS is [1, 32], caller should ensure no other constellations use this list
        val partialNavDataList: MutableList<PartialNavData> = (1..32).map { index -> PartialNavData(index) }.toMutableList()

        val obsDataList = Array<OverwritingQueue<ObsData>>(32)
        { _ -> OverwritingQueue(300) }

        val ionoCorrections: Ionospheric = Ionospheric()

        fun pushSubframe(prn: Int, messageType: Int, messageId: Int, rawData: ByteArray): PartialNavData? {
            // Already shifted right by 8, should always be 1
            if (messageType != 1) {
                Log.w("GNSS", "message type should not be 1, got $messageType")
                return null
            }
            // We only decode L1 C/A
            if (rawData.size != L1_CA_MESSAGE_LENGTH_BYTES) {
                Log.w("GNSS", "message length should be L1 C/A length ($L1_CA_MESSAGE_LENGTH_BYTES), got ${rawData.size}")
                return null
            }
            return when (messageId) {
                1 -> parseSubframe1(prn, rawData)
                2 -> parseSubframe2(prn, rawData)
                3 -> parseSubframe3(prn, rawData)
                4 -> {
                    parseSubframe4(rawData)  // Updates the global iono params
                    null
                }
                5 -> null  // nothing to update
                else -> null  // TODO: Handle invalid message id
            }
        }

        fun pushEphemerides(navDataList: List<NavData>) {
            for (navData in navDataList) {
                val index = navData.prn - 1
                partialNavDataList[index] = PartialNavData.fromFullyDecoded(navData)
            }
        }

        fun pushMeasurements(event: GnssMeasurementsEvent?) {
            event ?: return

            val clock = event.clock

            val fullBiasNs = if (clock.hasFullBiasNanos()) { clock.fullBiasNanos } else return
            val biasNs = if (clock.hasBiasNanos()) { clock.biasNanos.toLong() } else { 0L }

            val rxTimeNsSinceEpoch = clock.timeNanos - fullBiasNs - biasNs  // Note that this is since GPS Epoch
            val rxTimeNs = GpsTime(rxTimeNsSinceEpoch).towNs

            for (measurement in event.measurements) {
                // We only handle GPS satellites for now
                if (measurement.constellationType != GnssStatus.CONSTELLATION_GPS) continue
                // We only handle L1 C/A
                if (measurement.codeType != "C") continue

                // Skip if time is zero, or signal to noise ratio is below threshold
                if (measurement.cn0DbHz < 18 ||
                    measurement.state and (1 shl PageSurvey.TOW_DECODED_MEASUREMENT_STATE_BIT) == 0) continue

                obsDataList[measurement.svid - 1].push(ObsData(
                    prn = measurement.svid,
                    gpsTimeNs = rxTimeNsSinceEpoch,
                    rxTimeNs = rxTimeNs,
                    txTimeNs = measurement.receivedSvTimeNanos,
                    txTimeOffsetNs = measurement.timeOffsetNanos.toLong(),

                    pseudorangeRateMps = measurement.pseudorangeRateMetersPerSecond,
                    pseudorangeRateUncertaintyMps = measurement.pseudorangeRateUncertaintyMetersPerSecond,
                    adrMeters = measurement.accumulatedDeltaRangeMeters,
                    adrMetersValid = (GnssMeasurement.ADR_STATE_VALID and measurement.accumulatedDeltaRangeState) == GnssMeasurement.ADR_STATE_VALID,
                    adrUncertaintyMeters = measurement.accumulatedDeltaRangeUncertaintyMeters,
                    signalToNoiseRatioDb = measurement.snrInDb
                ))
            }
        }

        fun setIonoCorrections() {
            val hasBroadcostIono = ionoCorrections.alpha.any { it != 0.0 }

            for (partialNavData in partialNavDataList) {
                if (partialNavData.isComplete()) {
                    val navData = partialNavData.inner

                    val hasSuplIono = navData.iono.alpha.all { it == 0.0 }
                    if (hasSuplIono && hasBroadcostIono) {
                        navData.iono = ionoCorrections.clone()
                    }
                }
            }
        }

        fun clearData() {
            for (i in partialNavDataList.indices) {
                partialNavDataList[i] = PartialNavData(i + 1)
            }
            for (i in obsDataList.indices) {
                obsDataList[i].clear()
            }
            ionoCorrections.clear()
        }

        private fun parseSubframe1(prn: Int, rawData: ByteArray): PartialNavData? {
            val byteData = ByteData(rawData)

            var iodc = byteData.get(IODC1_POS) shl 8
            iodc = iodc or byteData.get(IODC2_POS)

            val partialNavData = findPartialNavData(prn, 1, iodc) ?: return null
            val navData = partialNavData.inner

            navData.iodc = iodc

            val week = byteData.get(WEEK_POS)
            navData.weekNo = getGpsWeekWithRollover(week).toDouble()

            val uraIndex = byteData.get(URA_POS)
            navData.svAccuracy = computeNominalSvAccuracy(uraIndex)

            val svHealth = byteData.get(SV_HEALTH_POS)
            navData.svHealth = svHealth.toDouble()

            val tgd = byteData.get(TGD_POS)
            navData.tgd = tgd * POW_2_NEG_31

            val toc = byteData.get(TOC_POS)
            navData.t0 = toc * POW_2_4

            val af2 = byteData.get(AF2_POS).toByte()
            navData.svClockDriftRate = af2 * POW_2_NEG_55

            val af1 = byteData.get(AF1_POS).toShort()
            navData.svClockDrift = af1 * POW_2_NEG_43

            var af0 = byteData.get(AF0_POS)
            af0 = getTwosComplement(af0, AF0_POS.length)
            navData.svClockBias = af0 * POW_2_NEG_31

            onParsingDone(partialNavData, subframeId = 1)
            return partialNavData
        }

        // Please refer to `handleSecondSubframe` in the example
        private fun parseSubframe2(prn: Int, rawData: ByteArray): PartialNavData? {
            val byteData = ByteData(rawData)

            val iode = byteData.get(IODE1_POS)

            val partialNavData = findPartialNavData(prn, 2, iode) ?: return null
            val navData = partialNavData.inner

            navData.iode = iode

            val crs = byteData.get(CRS_POS).toShort()
            navData.crs = crs * POW_2_NEG_5

            val deltaN = byteData.get(DELTA_N_POS).toShort()
            navData.deltaN = deltaN * POW_2_NEG_43 * Math.PI

            val m0 = u32From8And24(M0_INDEX8, M0_INDEX24, rawData).toInt()
            navData.m0 = m0 * POW_2_NEG_31 * Math.PI

            val cuc = byteData.get(CUC_POS).toShort()
            navData.cuc = cuc * POW_2_NEG_29

            val ecc = u32From8And24(E_INDEX8, E_INDEX24, rawData)
            navData.ecc = ecc * POW_2_NEG_33

            val cus = byteData.get(CUS_POS).toShort()
            navData.cus = cus * POW_2_NEG_29

            val sqrtA = u32From8And24(A_INDEX8, A_INDEX24, rawData)
            navData.sqrtA = sqrtA * POW_2_NEG_19

            val t0 = byteData.get(TOE_POS)
            navData.t0 = t0 * POW_2_4

            onParsingDone(partialNavData, subframeId = 2)
            return partialNavData
        }

        private fun parseSubframe3(prn: Int, rawData: ByteArray): PartialNavData? {
            val byteData = ByteData(rawData)

            val iode = byteData.get(IODE2_POS)

            val partialNavData = findPartialNavData(prn, 3, iode) ?: return null
            val navData = partialNavData.inner

            navData.iode = iode

            val cic = byteData.get(CIC_POS).toShort()
            navData.cic = cic * POW_2_NEG_29

            val omega0 = u32From8And24(O0_INDEX8, O0_INDEX24, rawData).toInt()
            navData.omega0 = omega0 * POW_2_NEG_31 * Math.PI

            val omega = u32From8And24(O_INDEX8, O_INDEX24, rawData).toInt()
            navData.omega = omega * POW_2_NEG_31 * Math.PI

            var dOmega = byteData.get(ODOT_POS)
            dOmega = getTwosComplement(dOmega, ODOT_POS.length)
            navData.dOmega = dOmega * POW_2_NEG_43 * Math.PI

            val cis = byteData.get(CIS_POS).toShort()
            navData.cis = cis * POW_2_NEG_29

            val i0 = u32From8And24(I0_INDEX8, I0_INDEX24, rawData).toInt()
            navData.i0 = i0 * POW_2_NEG_31 * Math.PI

            val crc = byteData.get(CRC_POS).toShort()
            navData.crc = crc * POW_2_NEG_5

            var dI = byteData.get(IDOT_POS)
            dI = getTwosComplement(dI, IDOT_POS.length)
            navData.dI = dI * POW_2_NEG_43 * Math.PI

            onParsingDone(partialNavData, subframeId = 3)
            return partialNavData
        }

        private fun parseSubframe4(rawData: ByteArray) {
            val byteData = ByteData(rawData)

            val pageId = byteData.get(BytePos(62, 6))
            if (pageId != IONOSPHERIC_PARAMETERS_PAGE_18_SV_ID) {
                // We only care to decode ionospheric parameters for now
                return
            }

            val a0 = byteData.get(A0_POS)
            val a1 = byteData.get(A1_POS)
            val a2 = byteData.get(A2_POS)
            val a3 = byteData.get(A3_POS)
            val alpha = doubleArrayOf(
                a0 * POW_2_NEG_30,
                a1 * POW_2_NEG_27,
                a2 * POW_2_NEG_24,
                a3 * POW_2_NEG_24,
            )

            val b0 = byteData.get(B0_POS)
            val b1 = byteData.get(B1_POS)
            val b2 = byteData.get(B2_POS)
            val b3 = byteData.get(B3_POS)
            val beta = doubleArrayOf(
                b0 * POW_2_11,
                b1 * POW_2_14,
                b2 * POW_2_16,
                b3 * POW_2_16,
            )

            ionoCorrections.alpha = alpha
            ionoCorrections.beta = beta
        }

        private fun onParsingDone(partialNavData: PartialNavData, subframeId: Int) {
            partialNavData.addSubframe(subframeId)
            if (!partialNavData.isComplete()) return

            // Derive date from week and TOE (approximate, ignoring leap seconds)
            val navData = partialNavData.inner
            val gpsEpoch = DateTime(1980, 1, 6, 0, 0, 0).withZoneRetainFields(DateTimeZone.UTC)
            val totalSeconds = (navData.weekNo * 604800) + navData.t0
            val dateTime = gpsEpoch.plusSeconds(totalSeconds.toInt())
            navData.dateTime = dateTime

            // Transmission time could be from TOW * 6, but approximate with TOE
            navData.transmissionTimeOfMsg = navData.t0
        }

        private fun u32From8And24(index8: Int, index24: Int, rawData: ByteArray): Long {
            val byteData = ByteData(rawData)
            var result = byteData.get(BytePos(index8, 8)).toLong() shl 24
            result = result or byteData.get(BytePos(index24, 24)).toLong()
            return result
        }

        private fun getTwosComplement(value: Int, bitLength: Int): Int {
            val msbMask = 1 shl (bitLength - 1)
            val msb = value and msbMask
            if (msb == 0) {
                // the value is positive
                return value
            }

            val valueBitMask = (1 shl bitLength) - 1
            val extendedSignMask = INTEGER_RANGE - valueBitMask
            return value or extendedSignMask
        }

        private fun getGpsWeekWithRollover(gpsWeek: Int): Int {
            val gpsTimeSec = GpsTime.fromUnixNow().secs
            val rolloverCycles = gpsTimeSec / GPS_CYCLE_SECS
            val rolloverWeeks = rolloverCycles.toInt() * GPS_CYCLE_WEEKS
            return gpsWeek + rolloverWeeks
        }

        private fun computeNominalSvAccuracy(uraIndex: Int): Double {
            if (uraIndex < 0 || uraIndex >= 15) {
                return Double.NaN
            }
            when (uraIndex) {
                1 -> return 2.8
                3 -> return 5.7
                5 -> return 11.3
            }
            val exponent = if (uraIndex < 6) { 1 + (uraIndex / 2) } else { uraIndex - 2 }
            return 2.0.pow(exponent)
        }

        private fun findPartialNavData(prn: Int, subframeId: Int, issueOfData: Int): PartialNavData? {
            var partialNavData = partialNavDataList[prn - 1]
            val decodeStatus = partialNavData.decodeStatus(prn, subframeId, issueOfData)

            // Find out if we have fully decoded up-to-date ephemeris first
            if (partialNavData.isComplete()) {
                if (decodeStatus.isDone()) return null
                // Reset the NavData
                partialNavData = PartialNavData(prn)
                return partialNavData
            }
            if (partialNavData.isNew()) {
                return partialNavData
            }
            if (decodeStatus.isDone()) return null
            if (decodeStatus.isReset()) {
                // Reset the NavData
                partialNavData = PartialNavData(prn)
                return partialNavData
            }

            var tempIode = Int.MAX_VALUE
            var hasIode = false
            val navData = partialNavData.inner

            if (partialNavData.hasDecodedSubframe(1)) {
                hasIode = true
                tempIode = navData.iodc and IODE_TO_IODC_MASK
            }
            if (partialNavData.hasDecodedSubframe(2) ||
                partialNavData.hasDecodedSubframe(3)) {
                hasIode = true
                tempIode = navData.iode
            }

            val canContinueDecoding = when (subframeId) {
                1 -> {
                    val iode = issueOfData and IODE_TO_IODC_MASK
                    !hasIode || (tempIode == iode)
                }
                in 2..3 -> {
                    !hasIode || (tempIode == issueOfData)
                }
                in 4..5 -> true
                else -> throw IllegalArgumentException("Invalid subframe: $subframeId")
            }
            if (!canContinueDecoding) {
                // Reset the NavData
                partialNavData = PartialNavData(prn)
            }
            return partialNavData
        }

        fun fullyDecodedNavDataCount(): Int {
            var count = 0
            for (navData in partialNavDataList) {
                if (navData.isComplete()) {
                    count += 1
                }
            }
            return count
        }
    }

    class ByteData(val rawData: ByteArray) {
        fun get(bytePos: BytePos): Int {
            var result = 0

            for (i in 0 until bytePos.length) {
                var workingIndex = bytePos.index + i
                val wordIndex = workingIndex / GpsEphemerisDecoder.WORD_SIZE_BITS
                // account for 2 bit padding for every 30 bit word
                workingIndex += (wordIndex + 1) * GpsEphemerisDecoder.WORD_PADDING_BITS
                val byteIndex = workingIndex / 8
                val byteOffset = workingIndex % 8

                val raw = rawData[byteIndex]
                // account for zero-based indexing
                val shiftOffset = 8 - 1 - byteOffset
                val mask = 1 shl shiftOffset
                var bit = raw.toInt() and mask
                bit = bit shr shiftOffset

                // account for zero-based indexing
                result = result or (bit shl bytePos.length - 1 - i)
            }
            return result
        }
    }
}