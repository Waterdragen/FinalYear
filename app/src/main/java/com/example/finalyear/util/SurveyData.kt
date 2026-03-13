package com.example.finalyear.util

import android.util.Log
import com.example.finalyear.GpsTime
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsDataWithRange
import kotlin.math.abs

data class SurveyData private constructor (
    val baseName: String,
    val navDataList: List<NavData>,
    val obsDataList: List<ObsDataWithRange>,
) {
    companion object {
        private const val FOUR_HOURS_IN_NS = 1_000_000_000L * 3600 * 4

        fun new(baseName: String, navDataList: List<NavData>, obsDataList: List<ObsDataWithRange>): SurveyData {
            val navDataMap = mutableMapOf<Int, NavData>()
            for (navData in navDataList) {
                navDataMap[navData.prn] = navData  // later ones overwrite earlier ones
            }

            val filteredObsDataList = obsDataList.filter {
                val obsData = it.inner
                val navData = navDataMap[obsData.prn] ?: return@filter false  // ensure all observations has ephemerides
                val navDataGpsTime = GpsTime.fromDateTime(navData.dateTime).nanos
                val obsDataGpsTime = obsData.gpsTimeNs - obsData.fullBiasNs - obsData.biasNs
                abs(navDataGpsTime - obsDataGpsTime) < FOUR_HOURS_IN_NS  // ensure ephemerides are not expired
            }

            val filteredNavDataList = arrayListOf<NavData>()
            for (obsData in filteredObsDataList) {
                val navData = navDataMap[obsData.inner.prn] ?: continue  // ensure all ephemerides have observations
                filteredNavDataList.add(navData)
            }
            require(filteredNavDataList.size == filteredObsDataList.size)

            return SurveyData(
                baseName = baseName,
                navDataList = filteredNavDataList,
                obsDataList = filteredObsDataList
            )
        }
    }
}
