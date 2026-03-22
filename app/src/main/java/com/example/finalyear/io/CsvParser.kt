package com.example.finalyear.io

import com.example.finalyear.core.HkStation
import com.example.finalyear.core.IonoModel
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import org.joda.time.DateTime
import java.io.File

class CsvParser(val lines: Iterator<String>) {
    companion object {
        fun tryFromFile(file: File): CsvParser? {
            if (!file.exists()) return null
            return CsvParser(
                lines = file.bufferedReader().lines().iterator()
            )
        }
    }

    // TODO: This is a non-standard CSV parsing method, we assume no commas in between

    fun tryParseNavDataList(): List<NavData>? {
        val header = if (lines.hasNext()) lines.next() else return null
        val headerMap = parseHeader(header)

        // Any missing fields short circuits
        val prnPos = headerMap["prn"] ?: return null
        val dateTimePos = headerMap["dateTime"] ?: return null

        val alphaPos = arrayOf(
            headerMap["alpha0"],
            headerMap["alpha1"],
            headerMap["alpha2"],
            headerMap["alpha3"],
        )
        val betaPos = arrayOf(
            headerMap["beta0"],
            headerMap["beta1"],
            headerMap["beta2"],
            headerMap["beta3"],
        )

        val navDataList = arrayListOf<NavData>()

        // We do a lossy conversion here, any bad line is skipped
        for (line in lines) {
            val words = line.split(',')

            val prn = words.getOrNull(prnPos)?.toIntOrNull() ?: continue
            val navData = NavData(prn)

            val dateTimeStr = words.getOrNull(dateTimePos) ?: continue
            navData.dateTime = try  {
                DateTime.parse(dateTimeStr)
            } catch (e: Exception) {
                continue
            }

            navData.svClockBias = headerMap["svClockBias"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.svClockDrift = headerMap["svClockDrift"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.svClockDriftRate = headerMap["svClockDriftRate"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.iode = headerMap["iode"]?.let { pos -> words.getOrNull(pos)?.toIntOrNull() } ?: continue
            navData.crs = headerMap["crs"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.deltaN = headerMap["deltaN"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.m0 = headerMap["m0"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.cuc = headerMap["cuc"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.ecc = headerMap["ecc"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.cus = headerMap["cus"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.sqrtA = headerMap["sqrtA"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.t0 = headerMap["t0"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.cic = headerMap["cic"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.omega0 = headerMap["omega0"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.cis = headerMap["cis"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.i0 = headerMap["i0"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.crc = headerMap["crc"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.omega = headerMap["omega"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.dOmega = headerMap["dOmega"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.dI = headerMap["dI"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.codeL2 = headerMap["codeL2"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.weekNo = headerMap["weekNo"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.l2PDataFlag = headerMap["l2PDataFlag"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.svAccuracy = headerMap["svAccuracy"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.svHealth = headerMap["svHealth"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.tgd = headerMap["tgd"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.iodc = headerMap["iodc"]?.let { pos -> words.getOrNull(pos)?.toIntOrNull() } ?: continue
            navData.transmissionTimeOfMsg = headerMap["transmissionTimeOfMsg"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue
            navData.fitInterval = headerMap["fitInterval"]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: continue


            val ionoCorrection = IonoModel()
            for (i in 0 until 4) {
                val alpha = alphaPos[i]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: 0.0
                val beta = betaPos[i]?.let { pos -> words.getOrNull(pos)?.toDoubleOrNull() } ?: 0.0
                ionoCorrection.alpha[i] = alpha
                ionoCorrection.beta[i] = beta
            }
            navData.ionoCorrection = ionoCorrection

            navDataList.add(navData)
        }
        return navDataList
    }

    fun tryParseObsDataList(): List<ObsData>? {
        val header = if (lines.hasNext()) lines.next() else return null
        val headerMap = parseHeader(header)

        val prnPos = headerMap["prn"] ?: return null
        val gpsTimeNsPos = headerMap["gpsTimeNs"] ?: return null
//        val arrivalTimeNsPos = headerMap["arrivalTimeNs"] ?: return null  // old
//        val recvSvTimeNsPos = headerMap["recvSvTimeNs"] ?: return null  // old
        val rxTimeNsPos = headerMap["rxTimeNs"] ?: return null
        val txTimeNsPos = headerMap["txTimeNs"] ?: return null
        val txTimeOffsetNsPos = headerMap["txTimeOffsetNs"] ?: return null

        val pseudorangeRateMpsPos = headerMap["pseudorangeRateMps"] ?: return null
        val pseudorangeRateUncertaintyMpsPos = headerMap["pseudorangeRateUncertaintyMps"] ?: return null
        val adrMetersPos = headerMap["adrMeters"] ?: return null
        val adrMetersValidPos = headerMap["adrMetersValid"] ?: return null
        val adrUncertaintyMetersPos = headerMap["adrUncertaintyMeters"] ?: return null
        val signalToNoiseRatioDbPos = headerMap["signalToNoiseRatioDb"] ?: return null

        val obsDataList = arrayListOf<ObsData>()

        for (line in lines) {
            val words = line.split(',')

            val obsData = ObsData (
                prn = words.getOrNull(prnPos)?.toIntOrNull() ?: continue,
                gpsTimeNs = words.getOrNull(gpsTimeNsPos)?.toLongOrNull() ?: continue,
//                arrivalTowNs = words.getOrNull(arrivalTimeNsPos)?.toLongOrNull() ?: continue,
//                recvSvTowNs = words.getOrNull(recvSvTimeNsPos)?.toLongOrNull() ?: continue,
                rxTimeNs = words.getOrNull(rxTimeNsPos)?.toLongOrNull() ?: continue,
                txTimeNs = words.getOrNull(txTimeNsPos)?.toLongOrNull() ?: continue,
                txTimeOffsetNs = words.getOrNull(txTimeOffsetNsPos)?.toLongOrNull() ?: continue,

                pseudorangeRateMps = words.getOrNull(pseudorangeRateMpsPos)?.toDoubleOrNull() ?: continue,
                pseudorangeRateUncertaintyMps = words.getOrNull(pseudorangeRateUncertaintyMpsPos)?.toDoubleOrNull() ?: continue,
                adrMeters = words.getOrNull(adrMetersPos)?.toDoubleOrNull() ?: continue,
                adrMetersValid = words.getOrNull(adrMetersValidPos)?.toBooleanStrictOrNull() ?: continue,
                adrUncertaintyMeters = words.getOrNull(adrUncertaintyMetersPos)?.toDoubleOrNull() ?: continue,
                signalToNoiseRatioDb = words.getOrNull(signalToNoiseRatioDbPos)?.toDoubleOrNull() ?: continue,
            )
            obsDataList.add(obsData)
        }

        return obsDataList
    }

    fun tryParseHkStationList(): List<HkStation>? {
        val header = if (lines.hasNext()) lines.next() else return null
        val headerMap = parseHeader(header)

        val stnNumPos = headerMap["stnNum"] ?: return null
        val localityPos = headerMap["locality"] ?: return null
        val eastingPos = headerMap["easting"] ?: return null
        val northingPos = headerMap["northing"] ?: return null
        val heightPos = headerMap["height"] ?: return null

        val hkStationList = arrayListOf<HkStation>()
        for (line in lines) {
            val words = parseCsvLineRobust(line)

            val hkStation = HkStation(
                stnNum = words.getOrNull(stnNumPos) ?: continue,
                locality = words.getOrNull(localityPos) ?: continue,
                easting = words.getOrNull(eastingPos)?.toDoubleOrNull() ?: continue,
                northing = words.getOrNull(northingPos)?.toDoubleOrNull() ?: continue,
                height = words.getOrNull(heightPos)?.toDoubleOrNull() ?: continue,
            )
            hkStationList.add(hkStation)
        }
        return hkStationList
    }
}

fun parseHeader(header: String, robust: Boolean = false): HashMap<String, Int> {
    var index = 0
    val headerMap = hashMapOf<String, Int>()
    val split = if (robust) { parseCsvLineRobust(header) } else { header.split(",") }
    for (word in split) {
        headerMap[word] = index
        index += 1
    }
    return headerMap
}

// takes into account of commas inside quotes
fun parseCsvLineRobust(line: String): List<String> {
    val chars = line.toList()
    val result = mutableListOf<String>()
    val builder = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val c = chars[i]

        when {
            c == '"' -> {
                if (inQuotes) {
                    // Check if next character is also quote (escaped quote)
                    if (i + 1 < line.length && chars[i + 1] == '"') {
                        builder.append('"')
                        i++ // skip the second quote
                    } else {
                        // End of quoted field
                        inQuotes = false
                    }
                } else {
                    // Start of quoted field
                    inQuotes = true
                }
            }

            c == ',' -> {
                if (inQuotes) {
                    // Comma inside quotes -> part of the field
                    builder.append(c)
                } else {
                    // End of field
                    result.add(builder.toString())
                    builder.clear()
                }
            }

            else -> {
                builder.append(c)
            }
        }
        i++
    }

    // Pushing the last field
    result.add(builder.toString())

    // Handle case of trailing comma (empty last field)
    if (line.isNotEmpty() && line.last() == ',' && !inQuotes) {
        result.add("")
    }

    return result
}