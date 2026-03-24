package com.example.finalyear

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.finalyear.core.HkStation
import com.example.finalyear.core.MyException
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import com.example.finalyear.core.Positioning
import com.example.finalyear.dgps.CustomRtcmParser
import com.example.finalyear.dgps.Rtcm
import com.example.finalyear.io.CsvParser
import com.example.finalyear.coord.Hk1980
import com.example.finalyear.util.SurveyData
import java.io.File
import kotlin.math.*

class PageSurveyStore : Fragment() {
    lateinit var btnLoadCsv: Button
    lateinit var btnCalculate: Button
    lateinit var btnLoadStations: ImageButton
    lateinit var btnClearStation: ImageButton

    lateinit var textLoadedFile: TextView
    lateinit var textNumNavSat: TextView
    lateinit var textNumObs: TextView
    lateinit var textSppResult: TextView
    lateinit var textDgnssResult: TextView
    lateinit var textStationName: TextView
    lateinit var textStationPos: TextView
    lateinit var textSppDiffResult: TextView
    lateinit var textDgnssDiffResultE: TextView
    lateinit var textDgnssDiffResultN: TextView
    lateinit var textDgnssDiffResultH: TextView
    lateinit var frameSurveyData: LinearLayout

    var surveyDataDgnss: SurveyData? = null
    var surveyDataSpp: SurveyData? = null
    var calculatedSppEnh: Hk1980.Grid? = null
    var calculatedDgnssEnh: Hk1980.Grid? = null
    var hkStationList: List<HkStation>? = null
    var nearestHkStationList: List<HkStation> = arrayListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.page_survey_store, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnLoadCsv = view.findViewById(R.id.btnLoadCsv)
        btnLoadCsv.setOnClickListener { onBtnLoadCsvPressed() }
        btnCalculate = view.findViewById(R.id.btnCalculate)
        btnCalculate.setOnClickListener { onBtnCalculatePressed() }
        btnLoadStations = view.findViewById(R.id.btnLoadStations)
        btnLoadStations.setOnClickListener { onBtnLoadStationsPressed() }
        btnClearStation = view.findViewById(R.id.btnClearStation)
        btnClearStation.setOnClickListener { onBtnClearStationPressed() }

        textLoadedFile = view.findViewById(R.id.textLoadedFile)
        textNumNavSat = view.findViewById(R.id.textNumNavSat)
        textNumObs = view.findViewById(R.id.textNumObs)
        textSppResult = view.findViewById(R.id.textSppResult)
        textDgnssResult = view.findViewById(R.id.textDgnssResult)
        textStationName = view.findViewById(R.id.textStationName)
        textStationPos = view.findViewById(R.id.textStationPos)
        textSppDiffResult = view.findViewById(R.id.textSppDiffResult)
        textDgnssDiffResultE = view.findViewById(R.id.textDgnssDiffE)
        textDgnssDiffResultN = view.findViewById(R.id.textDgnssDiffN)
        textDgnssDiffResultH = view.findViewById(R.id.textDgnssDiffH)

        frameSurveyData = view.findViewById(R.id.frameSurveyData)
    }

    private fun onBtnLoadCsvPressed() {
        val files = listInternalNavCsvFiles().sortedBy { it.name }
        val names = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose a CSV file")
            .setItems(names) { _, index ->
                loadNavCsvFile(files[index])
            }
            .show()
    }

    private fun onBtnCalculatePressed() {
        processGpsPosition()
    }

    private fun onBtnLoadStationsPressed() {
        val files = listInternalStationsCsvFiles()
        val names = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose a CSV file")
            .setItems(names) { _, index ->
                loadStationCsvFile(files[index])
            }
            .show()
    }

    private fun onBtnClearStationPressed() {

    }

    private fun listInternalNavCsvFiles(): List<File> {
        val filePath = requireContext().filesDir
        if (!filePath.exists()) return emptyList()
        return filePath.listFiles { file ->
            file.isFile
                && file.name.endsWith("-nav.csv")
        }?.toList() ?: emptyList()
    }

    private fun listInternalStationsCsvFiles(): List<File> {
        val filePath = requireContext().filesDir
        if (!filePath.exists()) return emptyList()
        return filePath.listFiles { file ->
            file.isFile
                    && file.name.endsWith("benchmarks.csv")
        }?.toList() ?: emptyList()
    }


    private fun loadNavCsvFile(navFile: File) {
        val context = requireContext()
        val baseName: String
        val navDataList: List<NavData>?
        val obsDataList: List<ObsData>?
        val stationRtcmMapOptional: Map<Int, Rtcm>

        if (!navFile.name.endsWith("-nav.csv")) {
            showDialog("Error", "Mangled file name `${navFile.name}`, should have nav or obs suffix")
            return
        }

        val navCsvParser = CsvParser.tryFromFile(navFile)
        if (navCsvParser == null) {
            showDialog("Fatal", "Nav file does not exist after selection")
            return
        }
        baseName = navFile.name.removeSuffix("-nav.csv")
        val obsFileName = "$baseName-obs.csv"
        val obsCsvParser = CsvParser.tryFromFile(File(context.filesDir, obsFileName))
        if (obsCsvParser == null) {
            showDialog("Error", "Obs file does not exist")
            return
        }
        navDataList = navCsvParser.tryParseNavDataList()
        obsDataList = obsCsvParser.tryParseObsDataList()

        val dgnssFileName = "$baseName-dgnss.txt"
        val dgnssFile = File(context.filesDir, dgnssFileName)
        if (!dgnssFile.exists()) {
            showDialog("Error", "DGNSS file does not exist")
            Log.e("GNSS", "DGNSS file does not exist")
            return
        }
        stationRtcmMapOptional = try {
            val dgnssText = dgnssFile.readText(Charsets.UTF_8)
            CustomRtcmParser.deserializeStationRtcmMap(dgnssText) ?: throw Exception("Failed to parse DGNSS file")
        } catch (e: Exception) {
            showDialog("Error", "Error parsing custom DGNSS file")
            Log.e("GNSS", e.stackTraceToString())
            return
        }

        if (navDataList == null) {
            showDialog("Error", "Unable to parse nav data csv")
            return
        }
        if (obsDataList == null) {
            showDialog("Error", "unable to parse obs data csv")
            return
        }

        val obsDataListSpp = obsDataList.map { obsData -> obsData.computePseudorange() }
        val obsDataListDgnss = obsDataListSpp.mapNotNull{ obsData -> obsData.adjustPseudorange(stationRtcmMapOptional) }

        surveyDataSpp = SurveyData.new(baseName, navDataList, obsDataListSpp)
        val newSurveyData = SurveyData.new(baseName, navDataList, obsDataListDgnss)
        surveyDataDgnss = newSurveyData
        showDialog("Success", "Stored `$baseName` to survey data list")

        textLoadedFile.text = "Loaded file: ${navFile.name}"
        textNumNavSat.text = "Navigation satellite count: ${newSurveyData.navDataList.size}"
        textNumObs.text = "Number of observations: ${obsDataListSpp.size}"  // Number of raw measurements
        clearContext()
    }

    private fun loadStationCsvFile(stationFile: File) {
        val hkStationCsvParser = CsvParser.tryFromFile(stationFile)
        if (hkStationCsvParser == null) {
            showDialog("Warning", "File does not exist")
            return
        }
        val hkStationList = hkStationCsvParser.tryParseHkStationList()
        if (hkStationList == null) {
            showDialog("Warning", "Failed to parse stations")
            return
        }
        this.hkStationList = hkStationList
        computeErrorWithClosestHkStation()
        showDialog("Success", "Loaded HK stations list")
    }

    private fun showDialog(title: String, msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    private fun processGpsPosition() {
        val surveyDataSpp = surveyDataSpp ?: return
        val surveyDataDgnss = surveyDataDgnss ?: return

        val sppPosList = arrayListOf<Hk1980.Grid>()
        val dgnssPosList = arrayListOf<Hk1980.Grid>()

        val sppNavList = surveyDataSpp.navDataList
        val dgnssNavList = surveyDataDgnss.navDataList

        for (epoch in surveyDataSpp.obsDataListByEpoch.keys) {
            val sppObsList = surveyDataSpp.obsDataListByEpoch[epoch]!!  // Accessing key-value with its keys
            val dgnssObsList = surveyDataDgnss.obsDataListByEpoch[epoch]!!  // SPP and DGNSS obsDataListByEpoch should have the same keys

            try {
                val sppPos = Positioning.sppSingleEpoch(sppNavList, sppObsList)
                val dgnssPos = Positioning.sppSingleEpoch(dgnssNavList, dgnssObsList)

                sppPosList.add(Hk1980.Grid.fromWgs84Xyz(sppPos))
                dgnssPosList.add(Hk1980.Grid.fromWgs84Xyz(dgnssPos))
            } catch (e: MyException.NotEnoughSatellites) {
                showDialog("Not Enough satellites", e.toString())
                return
            } catch (e: MyException.LsConvergeFail) {
                continue
            }
        }

        val sppAvgPos = arrayOf(0.0, 0.0, 0.0)
        val dgnssAvgPos = arrayOf(0.0, 0.0, 0.0)
        for (sppPos in sppPosList) {
            sppAvgPos[0] += sppPos.e
            sppAvgPos[1] += sppPos.n
            sppAvgPos[2] += sppPos.h
        }
        for (dgnssPos in dgnssPosList) {
            dgnssAvgPos[0] += dgnssPos.e
            dgnssAvgPos[1] += dgnssPos.n
            dgnssAvgPos[2] += dgnssPos.h
        }
        for (i in 0 until 3) {
            sppAvgPos[i] /= sppPosList.size.toDouble()
            dgnssAvgPos[i] /= dgnssPosList.size.toDouble()
        }

        val sppEnh = Hk1980.Grid(sppAvgPos[0], sppAvgPos[1], sppAvgPos[2])
        val dgnssEnh = Hk1980.Grid(dgnssAvgPos[0], dgnssAvgPos[1], dgnssAvgPos[2])
        calculatedSppEnh = sppEnh
        calculatedDgnssEnh = dgnssEnh
        computeErrorWithClosestHkStation()
        handleSuccessfullyResolvedLocation(sppEnh, dgnssEnh)
    }

    private fun handleSuccessfullyResolvedLocation(sppEnh: Hk1980.Grid, dgnssEnh: Hk1980.Grid) {

        val sppText = String.format("Easting: %.3f\nNorthing: %.3f\nHeight: %.3f\n", sppEnh.e, sppEnh.n, sppEnh.h)
        val dgnssText = String.format("Easting: %.3f\nNorthing: %.3f\nHeight: %.3f\n", dgnssEnh.e, dgnssEnh.n, dgnssEnh.h)
        textSppResult.text = sppText
        textDgnssResult.text = dgnssText

        AlertDialog.Builder(requireContext())
            .setTitle("Success")
            .setMessage(null)
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    fun computeErrorWithClosestHkStation() {
        val sppEnh = calculatedSppEnh ?: return
        val dgnssEnh = calculatedDgnssEnh ?: return
        val hkStationList = hkStationList ?: return
        nearestHkStationList = HkStation.findNearest(
            hkStationList = hkStationList,
            easting = dgnssEnh.e,
            northing = dgnssEnh.n
        )
        val hkStation = nearestHkStationList.getOrNull(0) ?: return  // use the nearest station
        // If the station is close enough, we are probably surveying the station
        val dE = dgnssEnh.e - hkStation.easting
        val dN = dgnssEnh.n - hkStation.northing
        if (sqrt(dE * dE + dN * dN) > 100) return

        textStationName.text = "Station name: ${hkStation.stnNum} (${hkStation.locality})"
        textStationPos.text = String.format("Coordinates: (E %.3f, N %.3f, H %.3f)", hkStation.easting, hkStation.northing, hkStation.height)

        val sppDE = sppEnh.e - hkStation.easting
        val sppDN = sppEnh.n - hkStation.northing
        val sppDH = sppEnh.h - hkStation.height
        val dgnssDE = dgnssEnh.e - hkStation.easting
        val dgnssDN = dgnssEnh.n - hkStation.northing
        val dgnssDH = dgnssEnh.h - hkStation.height
        textSppDiffResult.text = String.format("dE: %+.2f\ndN: %+.2f\ndH: %+.2f\n", sppDE, sppDN, sppDH)
        textDgnssDiffResultE.text = String.format("dE: %+.2f", dgnssDE)
        textDgnssDiffResultN.text = String.format("dN: %+.2f", dgnssDN)
        textDgnssDiffResultH.text = String.format("dH: %+.2f", dgnssDH)

        dgnssDiffChangeColor(textDgnssDiffResultE, sppDE, dgnssDE)
        dgnssDiffChangeColor(textDgnssDiffResultN, sppDN, dgnssDN)
        dgnssDiffChangeColor(textDgnssDiffResultH, sppDH, dgnssDH)
    }

    fun dgnssDiffChangeColor(textView: TextView, deltaSpp: Double, deltaDgnss: Double) {
        val red = 0xff_dd4444u.toInt()
        val green = 0xff_44aa44u.toInt()
        val darkGray = 0xff_444444u.toInt()
        val improvedAbs = abs(deltaSpp) - abs(deltaDgnss)
        val improvedRel = improvedAbs / abs(deltaSpp)

        val color = if (abs(improvedAbs) < 1 || abs(improvedRel) < 0.02) {
            darkGray
        } else if (improvedAbs > 0) {
            green
        } else {
            red
        }
        textView.setTextColor(color)
    }

    fun clearContext() {
        calculatedSppEnh = null
        calculatedDgnssEnh = null
        textSppResult.text = ""
        textSppDiffResult.text = ""
        textDgnssResult.text = ""
        textDgnssDiffResultE.text = ""
        textDgnssDiffResultN.text = ""
        textDgnssDiffResultH.text = ""
        textStationName.text = ""
        textStationPos.text = ""
    }

}