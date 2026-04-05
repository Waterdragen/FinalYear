package com.example.finalyear.util

import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsDataWithRange

data class SurveyData private constructor (
    val baseName: String,
    val navDataList: List<NavData>,
    val obsDataListByEpoch: Map<Long, List<ObsDataWithRange>>,
    val rawNavDataCount: Int,
) {
    companion object {
        fun new(baseName: String, navDataList: List<NavData>, obsDataList: List<ObsDataWithRange>): SurveyData {  // obsDataList is raw pseudoranges
            val rawNavDataCount = navDataList.size
            val obsDataGrouped = obsDataList.groupBy { it.inner.prn }

            // Ensure all NavData maps to ObsData
            val navDataMap = mutableMapOf<Int, NavData>()
            for (navData in navDataList) {
                if (obsDataGrouped.containsKey(navData.prn)) {  // ensure all ephemerides have observations
                    navDataMap[navData.prn] = navData  // later ones overwrite earlier ones
                }
            }

            // Sort NavData by prn
            val filteredNavDataList = navDataMap.values.toList().sortedBy { it.prn }

            // Group ObsData by epoch, sort individual list by prn
            val obsDataListByEpoch = obsDataList.groupBy { it.inner.rxTimeNs }.toMutableMap()
            for ((epoch, lst) in obsDataListByEpoch)  {
                obsDataListByEpoch[epoch] = lst
                    .filter {
                        navDataMap.containsKey(it.inner.prn)  // ensure all observations have ephemerides
                        && it.inner.signalToNoiseRatioDb > 15.0  // ensure signal to noise ratio is not too weak or obstructed (weak filter)
                    }
                    .sortedBy { it.inner.prn }
            }

            return SurveyData(
                baseName = baseName,
                navDataList = filteredNavDataList,
                obsDataListByEpoch = obsDataListByEpoch,
                rawNavDataCount = rawNavDataCount,
            )
        }
    }
}
