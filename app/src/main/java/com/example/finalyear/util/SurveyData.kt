package com.example.finalyear.util

import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsDataWithRange

data class SurveyData private constructor (
    val baseName: String,
    val navDataList: List<NavData>,
//    val obsDataList: List<ObsDataWithRange>,
    val obsDataListByEpoch: Map<Long, List<ObsDataWithRange>>,
) {
    companion object {
        fun new(baseName: String, navDataList: List<NavData>, obsDataList: List<ObsDataWithRange>): SurveyData {  // obsDataList is raw pseudoranges
            val obsDataGrouped = obsDataList.groupBy { it.inner.prn }

            val navDataMap = mutableMapOf<Int, NavData>()
            for (navData in navDataList) {
                if (obsDataGrouped.containsKey(navData.prn)) {  // ensure all ephemerides have observations
                    navDataMap[navData.prn] = navData  // later ones overwrite earlier ones
                }
            }

            val filteredNavDataList = navDataMap.values.toList().sortedBy { it.prn }

            val obsDataListByEpoch = obsDataList.groupBy { it.inner.rxTimeNs }.toMutableMap()
            for ((epoch, lst) in obsDataListByEpoch)  {
                obsDataListByEpoch[epoch] = lst
                    .filter { navDataMap.containsKey(it.inner.prn) }  // ensure all observations have ephemerides
                    .sortedBy { it.inner.prn }
            }

            val obsDataListIter = obsDataListByEpoch.entries.iterator()

            while (obsDataListIter.hasNext()) {
                val (_, lst) = obsDataListIter.next()
                if (lst.size != filteredNavDataList.size) {  // missing measurements for epoch
                    obsDataListIter.remove()
                }
                var avgSnrDb = 0.0
                for (obsData in lst) {
                    avgSnrDb += obsData.inner.signalToNoiseRatioDb
                }
                avgSnrDb /= lst.size
                if (avgSnrDb < 10) {
                    obsDataListIter.remove()
                }
            }


            return SurveyData(
                baseName = baseName,
                navDataList = filteredNavDataList,
//                obsDataList = filteredObsDataList,
                obsDataListByEpoch = obsDataListByEpoch,
            )
        }
    }
}
