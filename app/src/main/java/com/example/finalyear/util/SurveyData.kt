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
                    .filter { navDataMap.containsKey(it.inner.prn) }  // ensure all observations have ephemerides
                    .sortedBy { it.inner.prn }
            }

            // Ensure all ObsData maps to NavData
            val obsDataListIter = obsDataListByEpoch.entries.iterator()

            while (obsDataListIter.hasNext()) {
                val (_, lst) = obsDataListIter.next()
                if (lst.size != filteredNavDataList.size) {  // missing measurements for epoch
                    obsDataListIter.remove()
                    continue
                }
                // SNR threshold filter
                var avgSnrDb = 0.0
                for (obsData in lst) {
                    avgSnrDb += obsData.inner.signalToNoiseRatioDb
                }
                avgSnrDb /= lst.size
                if (avgSnrDb < 5) {
                    obsDataListIter.remove()
                }
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
