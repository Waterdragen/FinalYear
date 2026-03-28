package com.example.finalyear.io

import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import java.io.File
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.KClass

object CsvWriter {
    val NAV_DATA_HEADER = navDataheader()
    val OBS_DATA_HEADER = obsDataHeader()

    fun writeNavDataHeader(file: File) {
        file.appendText(NAV_DATA_HEADER)
    }

    fun writeObsDataHeader(file: File) {
        file.appendText(OBS_DATA_HEADER)
    }

    fun writeNavDataRow(file: File, navData: NavData) {
        val sb = StringBuilder()
        sb.append(navData.prn.toString()); sb.append(',')
        sb.append(navData.dateTime.toString()); sb.append(',')
        sb.append(navData.svClockBias.toString()); sb.append(',')
        sb.append(navData.svClockDrift.toString()); sb.append(',')
        sb.append(navData.svClockDriftRate.toString()); sb.append(',')
        sb.append(navData.iode.toString()); sb.append(',')
        sb.append(navData.crs.toString()); sb.append(',')
        sb.append(navData.deltaN.toString()); sb.append(',')
        sb.append(navData.m0.toString()); sb.append(',')
        sb.append(navData.cuc.toString()); sb.append(',')
        sb.append(navData.ecc.toString()); sb.append(',')
        sb.append(navData.cus.toString()); sb.append(',')
        sb.append(navData.sqrtA.toString()); sb.append(',')
        sb.append(navData.t0.toString()); sb.append(',')
        sb.append(navData.cic.toString()); sb.append(',')
        sb.append(navData.omega0.toString()); sb.append(',')
        sb.append(navData.cis.toString()); sb.append(',')
        sb.append(navData.i0.toString()); sb.append(',')
        sb.append(navData.crc.toString()); sb.append(',')
        sb.append(navData.omega.toString()); sb.append(',')
        sb.append(navData.dOmega.toString()); sb.append(',')
        sb.append(navData.dI.toString()); sb.append(',')
        sb.append(navData.codeL2.toString()); sb.append(',')
        sb.append(navData.weekNo.toString()); sb.append(',')
        sb.append(navData.l2PDataFlag.toString()); sb.append(',')
        sb.append(navData.svAccuracy.toString()); sb.append(',')
        sb.append(navData.svHealth.toString()); sb.append(',')
        sb.append(navData.tgd.toString()); sb.append(',')
        sb.append(navData.iodc.toString()); sb.append(',')
        sb.append(navData.transmissionTimeOfMsg.toString()); sb.append(',')
        sb.append(navData.fitInterval.toString()); sb.append(',')
        sb.append(navData.spare1.toString()); sb.append(',')
        sb.append(navData.spare2.toString()); sb.append(',')
        for (i in 0 until 4) {
            sb.append(navData.iono.alpha[i].toString()); sb.append(',')
        }
        for (i in 0 until 4) {
            sb.append(navData.iono.beta[i].toString()); sb.append(',')
        }
        sb.append('\n')

        file.appendText(sb.toString())
    }

    fun writeObsDataRow(file: File, obsData: ObsData) {
        val sb = StringBuilder()
        sb.append(obsData.prn.toString()); sb.append(',')
        sb.append(obsData.gpsTimeNs.toString()); sb.append(',')
        sb.append(obsData.rxTimeNs.toString()); sb.append(',')
        sb.append(obsData.txTimeNs.toString()); sb.append(',')
        sb.append(obsData.txTimeOffsetNs.toString()); sb.append(',')

        sb.append(obsData.pseudorangeRateMps.toString()); sb.append(',')
        sb.append(obsData.pseudorangeRateUncertaintyMps.toString()); sb.append(',')
        sb.append(obsData.adrMeters.toString()); sb.append(',')
        sb.append(obsData.adrMetersValid.toString()); sb.append(',')
        sb.append(obsData.adrUncertaintyMeters.toString()); sb.append(',')
        sb.append(obsData.signalToNoiseRatioDb.toString()); sb.append(',')
        sb.append('\n')

        file.appendText(sb.toString())
    }

    private fun navDataheader(): String {
        val names = fieldNames(NavData::class).toMutableList()
        names.removeLast()  // remove field "iono"
        names.add("alpha0")
        names.add("alpha1")
        names.add("alpha2")
        names.add("alpha3")
        names.add("beta0")
        names.add("beta1")
        names.add("beta2")
        names.add("beta3")
        return csvHeader(names)
    }

    private fun obsDataHeader(): String {
        return csvHeader(fieldNames(ObsData::class))
    }

    private fun csvHeader(fieldNames: List<String>): String {
        val sb = StringBuilder()
        for (name in fieldNames) {
            sb.append(name)
            sb.append(',')
        }
        sb.append('\n')
        return sb.toString()
    }

    private fun <T: Any> fieldNames(clazz: KClass<T>): List<String> {
        return clazz.primaryConstructor?.parameters?.mapNotNull { it.name } ?: emptyList()
    }
}