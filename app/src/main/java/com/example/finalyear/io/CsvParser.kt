package com.example.finalyear.io

import android.util.Log
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import com.example.finalyear.core.HkStation
import com.example.finalyear.core.MyException
import com.example.finalyear.model.Ionospheric
import org.joda.time.DateTime
import java.io.File

object CsvParser {
    fun tryParseNavDataList(file: File): List<NavData> {
        if (!file.exists()) {
            throw MyException.FileNotFound(file)
        }
        return try {
            tryParseNavDataListInner(file)
        } catch (e: Exception) {
            Log.e("GNSS", e.stackTraceToString())
            throw MyException.CsvParseError(file)
        }
    }

    private fun tryParseNavDataListInner(file: File): List<NavData> {
        return csvReader().open(file) {
            readAllWithHeaderAsSequence().mapNotNull { row ->
                try {
                    val iono = Ionospheric(
                        alpha = doubleArrayOf(
                            row["alpha0"]?.toDoubleOrNull() ?: 0.0,
                            row["alpha1"]?.toDoubleOrNull() ?: 0.0,
                            row["alpha2"]?.toDoubleOrNull() ?: 0.0,
                            row["alpha3"]?.toDoubleOrNull() ?: 0.0
                        ),
                        beta = doubleArrayOf(
                            row["beta0"]?.toDoubleOrNull() ?: 0.0,
                            row["beta1"]?.toDoubleOrNull() ?: 0.0,
                            row["beta2"]?.toDoubleOrNull() ?: 0.0,
                            row["beta3"]?.toDoubleOrNull() ?: 0.0
                        )
                    )

                    NavData(
                        prn = row["prn"]?.toIntOrNull() ?: return@mapNotNull null,
                        dateTime = row["dateTime"]?.let { DateTime.parse(it) } ?: return@mapNotNull null,

                        svClockBias = row["svClockBias"]?.toDoubleOrNull() ?: 0.0,
                        svClockDrift = row["svClockDrift"]?.toDoubleOrNull() ?: 0.0,
                        svClockDriftRate = row["svClockDriftRate"]?.toDoubleOrNull() ?: 0.0,
                        iode = row["iode"]?.toIntOrNull() ?: 0,
                        crs = row["crs"]?.toDoubleOrNull() ?: 0.0,
                        deltaN = row["deltaN"]?.toDoubleOrNull() ?: 0.0,
                        m0 = row["m0"]?.toDoubleOrNull() ?: 0.0,
                        cuc = row["cuc"]?.toDoubleOrNull() ?: 0.0,
                        ecc = row["ecc"]?.toDoubleOrNull() ?: 0.0,
                        cus = row["cus"]?.toDoubleOrNull() ?: 0.0,
                        sqrtA = row["sqrtA"]?.toDoubleOrNull() ?: 0.0,
                        t0 = row["t0"]?.toDoubleOrNull() ?: 0.0,
                        cic = row["cic"]?.toDoubleOrNull() ?: 0.0,
                        omega0 = row["omega0"]?.toDoubleOrNull() ?: 0.0,
                        cis = row["cis"]?.toDoubleOrNull() ?: 0.0,
                        i0 = row["i0"]?.toDoubleOrNull() ?: 0.0,
                        crc = row["crc"]?.toDoubleOrNull() ?: 0.0,
                        omega = row["omega"]?.toDoubleOrNull() ?: 0.0,
                        dOmega = row["dOmega"]?.toDoubleOrNull() ?: 0.0,
                        dI = row["dI"]?.toDoubleOrNull() ?: 0.0,
                        codeL2 = row["codeL2"]?.toDoubleOrNull() ?: 0.0,
                        weekNo = row["weekNo"]?.toDoubleOrNull() ?: 0.0,
                        l2PDataFlag = row["l2PDataFlag"]?.toDoubleOrNull() ?: 0.0,
                        svAccuracy = row["svAccuracy"]?.toDoubleOrNull() ?: 0.0,
                        svHealth = row["svHealth"]?.toDoubleOrNull() ?: 0.0,
                        tgd = row["tgd"]?.toDoubleOrNull() ?: 0.0,
                        iodc = row["iodc"]?.toIntOrNull() ?: 0,
                        transmissionTimeOfMsg = row["transmissionTimeOfMsg"]?.toDoubleOrNull() ?: 0.0,
                        fitInterval = row["fitInterval"]?.toDoubleOrNull() ?: 0.0,

                        iono = iono
                    )
                } catch (e: Exception) {
                    null // skip bad rows
                }
            }.toList()
        }
    }

    fun tryParseObsDataList(file: File): List<ObsData> {
        if (!file.exists()) {
            throw MyException.FileNotFound(file)
        }
        return try {
            tryParseObsDataListInner(file)
        } catch (e: Exception) {
            Log.e("GNSS", e.stackTraceToString())
            throw MyException.CsvParseError(file)
        }
    }

    private fun tryParseObsDataListInner(file: File): List<ObsData> {
        if (!file.exists()) {
            throw MyException.FileNotFound(file)
        }

        return csvReader().open(file) {
            readAllWithHeaderAsSequence().mapNotNull { row ->
                try {
                    ObsData(
                        prn = row["prn"]?.toIntOrNull() ?: return@mapNotNull null,
                        gpsTimeNs = row["gpsTimeNs"]?.toLongOrNull() ?: return@mapNotNull null,
                        rxTimeNs = row["rxTimeNs"]?.toLongOrNull() ?: return@mapNotNull null,
                        txTimeNs = row["txTimeNs"]?.toLongOrNull() ?: return@mapNotNull null,
                        txTimeOffsetNs = row["txTimeOffsetNs"]?.toLongOrNull() ?: 0L,

                        pseudorangeRateMps = row["pseudorangeRateMps"]?.toDoubleOrNull() ?: 0.0,
                        pseudorangeRateUncertaintyMps = row["pseudorangeRateUncertaintyMps"]?.toDoubleOrNull() ?: 0.0,
                        adrMeters = row["adrMeters"]?.toDoubleOrNull() ?: 0.0,
                        adrMetersValid = row["adrMetersValid"]?.toBooleanStrictOrNull() ?: false,
                        adrUncertaintyMeters = row["adrUncertaintyMeters"]?.toDoubleOrNull() ?: 0.0,
                        signalToNoiseRatioDb = row["signalToNoiseRatioDb"]?.toDoubleOrNull() ?: 0.0
                    )
                } catch (e: Exception) {
                    null  // skip bad rows
                }
            }.toList()
        }
    }

    fun tryParseHkStationList(file: File): List<HkStation> {
        if (!file.exists()) {
            throw MyException.FileNotFound(file)
        }
        return try {
            tryParseHkStationListInner(file)
        } catch (e: Exception) {
            Log.e("GNSS", e.stackTraceToString())
            throw MyException.CsvParseError(file)
        }
    }

    private fun tryParseHkStationListInner(file: File): List<HkStation> {
        return csvReader().open(file) {
            readAllWithHeaderAsSequence().mapNotNull { row ->
                try {
                    HkStation(
                        stnNum = row["stnNum"] ?: return@mapNotNull null,
                        locality = row["locality"] ?: "",
                        easting = row["easting"]?.toDoubleOrNull() ?: return@mapNotNull null,
                        northing = row["northing"]?.toDoubleOrNull() ?: return@mapNotNull null,
                        height = row["height"]?.toDoubleOrNull() ?: return@mapNotNull null
                    )
                } catch (e: Exception) {
                    null
                }
            }.toList()
        }
    }
}