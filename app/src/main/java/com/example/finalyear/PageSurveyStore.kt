package com.example.finalyear

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.finalyear.core.MyException
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import com.example.finalyear.core.ObsDataWithRange
import com.example.finalyear.core.PositionAndRangeResidual
import com.example.finalyear.core.SatClockCorrection
import com.example.finalyear.dgps.CustomRtcmParser
import com.example.finalyear.dgps.Rtcm
import com.example.finalyear.io.CsvParser
import com.example.finalyear.util.Hk1980
import com.example.finalyear.util.SurveyData
import com.example.finalyear.util.Xyz
import org.ejml.dense.row.decomposition.TriangularSolver_DDRM
import org.ejml.dense.row.factory.DecompositionFactory_DDRM
import org.ejml.simple.SimpleMatrix
import java.io.File
import kotlin.math.*

class PageSurveyStore : Fragment() {
    companion object {
        const val AVERAGE_TRAVEL_TIME_SECONDS = 70e-3
        const val GPS_CHIP_WIDTH_T_C_SEC = 1e-6
        const val GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1
        const val GPS_DLL_AVERAGING_TIME_SEC = 20e-3
    }

    lateinit var btnLoadCsv: Button
    lateinit var btnCalculate: Button
    lateinit var textLoadedFile: TextView
    lateinit var textNumNavSat: TextView
    lateinit var textNumObs: TextView
    lateinit var frameSurveyData: LinearLayout

//    val surveyDataList: ArrayList<SurveyData> = arrayListOf()
var surveyData: SurveyData? = null

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
        btnCalculate.setOnClickListener { onBtnCalculatPressed() }
        textLoadedFile = view.findViewById(R.id.textLoadedFile)
        textNumNavSat = view.findViewById(R.id.textNumNavSat)
        textNumObs = view.findViewById(R.id.textNumObs)

        frameSurveyData = view.findViewById(R.id.frameSurveyData)
    }

    private fun onBtnLoadCsvPressed() {
        showCsvPickerDialog()
    }

    private fun onBtnCalculatPressed() {
        processGpsPosition()
    }

    private fun showCsvPickerDialog() {
        val files = listInternalCsvFiles()
        val names = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose a CSV file")
            .setItems(names) { _, index ->
                loadCsvFile(files[index])
            }
            .show()
    }

    private fun listInternalCsvFiles(): List<File> {
        val filePath = requireContext().filesDir
        if (!filePath.exists()) return emptyList()
        return filePath.listFiles { file ->
            file.isFile
                && file.name.endsWith("-nav.csv")
        }?.toList() ?: emptyList()
    }


    private fun loadCsvFile(navFile: File) {
        val context = requireContext()
        val baseName: String
        val navDataList: List<NavData>?
        val obsDataList: List<ObsData>?
        val stationRtcmMapOptional: Map<Int, Rtcm>?

        if (navFile.name.endsWith("-nav.csv")) {
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
            stationRtcmMapOptional = if (dgnssFile.exists()) {
                try {
                    val dgnssText = dgnssFile.readText(Charsets.UTF_8)
                    CustomRtcmParser.deserializeStationRtcmMap(dgnssText)
                } catch (e: Exception) {
                    Log.e("GNSS", e.stackTraceToString())
                    null
                }
            } else {
                Log.w("GNSS", "DGNSS file not found")
                null
            }
        } else {
            showDialog("Error", "Mangled file name `${navFile.name}`, should have nav or obs suffix")
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

        val obsDataListWithRange = computePseudoranges(obsDataList)
        if (stationRtcmMapOptional != null) {
            adjustPseudorange(obsDataListWithRange, stationRtcmMapOptional)
        }

        showDialog("Success", "Stored `$baseName` to survey data list")
        val newSurveyData = SurveyData.new(baseName, navDataList, obsDataListWithRange)
        surveyData = newSurveyData
//        surveyDataList.add(SurveyData(baseName, navDataList, obsDataListWithRange))

        textLoadedFile.text = "Loaded file: ${navFile.name}"
        textNumNavSat.text = "Navigation satellite count: ${newSurveyData.navDataList.size}"
        textNumObs.text = "Number of observations: ${newSurveyData.obsDataList.size}"
    }

    private fun showDialog(title: String, msg: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    private fun processGpsPosition(
//        surveyDataIndex: Int
    ) {
//        val surveyData = surveyDataList.getOrNull(surveyDataIndex) ?: return  // TODO: Handle index out of bounds logic
        val surveyData = surveyData ?: return

        val navList = surveyData.navDataList
        val obsList = surveyData.obsDataList
//        val navList = arrayListOf<NavData>()
//        val obsList = arrayListOf<ObsDataWithRange>()
        val posEcef = SimpleMatrix(8, 1)
        val posUncertaintyEnu = SimpleMatrix(6, 1)

//        for (obsData in surveyData.obsDataList) {
//            val obsTime = obsData.inner.gpsTimeNs
//            var matchNavData: NavData? = null
//            for (navData in surveyData.navDataList) {
//                if (navData.prn == obsData.inner.prn) {
//                    matchNavData = navData
//                    break
//                }
//            }
//            // No satellites with matching PRN found
//            if (matchNavData == null) {
//                continue
//            }
//
//            // Found a matching satellite with matching PRN, but expired
//            if (abs(GpsTime.fromDateTime(matchNavData.dateTime).nanos - obsTime) >= 1_000_000_000L * 3600 * 4) {
//                val minutesBetween = Minutes.minutesBetween(matchNavData.dateTime, GpsTime.fromNanos(obsTime).toDateTime()).minutes
//                Log.d("GNSS", "expired matching satellite, dateTime: ${matchNavData.dateTime}, obsTime: ${GpsTime.fromNanos(obsTime).toDateTime()}, minutes between: $minutesBetween")
//                continue
//            }
//            navList.add(matchNavData)
//            obsList.add(obsData)
//        }

        try {
            leastSquareAdjustment(navList, obsList, posEcef, posUncertaintyEnu)
//            leastSquareAdjustment2(navList, obsList, posEcef, posUncertaintyEnu)
            val ecefPos = Xyz.from(posEcef)
            showDialogSuccessfullyResolvedLocation(ecefPos)
        } catch (e: MyException.NotEnoughSatellites) {
            showDialog("Not Enough satellites", e.toString())
        }
    }

    private fun leastSquareAdjustment(navList: List<NavData>,
                                      obsList: List<ObsDataWithRange>,
                                      posEcef: SimpleMatrix,
                                      posUncertaintyEnu: SimpleMatrix) {
        var weekNum = -1.0
        for (item in navList) {
            if (item.weekNo > weekNum) {
                weekNum = item.weekNo
            }
        }
        var arrivalTowSec = -1.0
        for (item in obsList) {
            if (item.inner.rxTimeNs > arrivalTowSec) {
                arrivalTowSec = item.inner.rxTimeNs.toDouble() / 1e9
            }
        }

        // both ObsData and ObsDataWithRange implements clone()
        val mutMeasurements = obsList.map { obsData -> obsData.clone() }.toList()
        var numSats = obsList.size
        lateinit var geometryMatrix: SimpleMatrix  // Safety: the loop runs at least once and writes before reads
        lateinit var posAndRangeResidual: PositionAndRangeResidual  // Safety: the loop runs at least once and writes before reads

        if (numSats < 4) {
            throw MyException.NotEnoughSatellites("Not enough satellites, expected 4, got only $numSats")
        }

        var started = false
        var repeatLeastSquare = false

        while (!started || repeatLeastSquare) {
            started = true

            // Calculate sat pos and residuals
            posAndRangeResidual = MathFn.calculateSatPosAndPseudorangeResidual(
                navDataList = navList,
                obsDataList = obsList,
                posEcef = posEcef,
                arrivalTowSec = arrivalTowSec,
                weekNum = weekNum,
            )
            val satPosEcefMeters = posAndRangeResidual.satPosMeters

            // Calculate the geometry matrix according to "Global Positioning System: Theory and
            // Applications", Parkinson and Spilker page 413
            geometryMatrix = MathFn.calculateGeometryMatrix(satPosEcefMeters = satPosEcefMeters,
                                                            posEcef = posEcef)

            val covarMatrixMetersSq = posAndRangeResidual.covarMatrixMetersSq
            val det = covarMatrixMetersSq.determinant()

            var weightedGeometryMatrixInv: SimpleMatrix? = null
            val weightedGeometryMatrix = if (det < 1e-9) {
                geometryMatrix
            } else {
                weightedGeometryMatrixInv = covarMatrixMetersSq.invert()
                val hMatrix = MathFn.calculateHMatrix(weightedGeometryMatrixInv, geometryMatrix)
                hMatrix.mult(geometryMatrix.transpose()).mult(weightedGeometryMatrixInv)
            }

            // Calculate delta position meters
            // Equation 9 page 413 from "Global Positioning System: Theory and Applications", Parkinson
            // and Spilker
            var deltaPosMeters = MathFn.matrixByColVecMult(weightedGeometryMatrix,
                                                           posAndRangeResidual.deltaRangeMeters)

            // Apply corrections to the position estimate
            posEcef[0] += deltaPosMeters[0]
            posEcef[1] += deltaPosMeters[1]
            posEcef[2] += deltaPosMeters[2]
            posEcef[3] += deltaPosMeters[3]

            // Apply weighted least square

            var iterCount = 0

            while (abs(deltaPosMeters[0])
                   + abs(deltaPosMeters[1])
                   + abs(deltaPosMeters[2]) >= 4e-8) {
                // We don't do iono and tropo corrections for now

                posAndRangeResidual = MathFn.calculateSatPosAndPseudorangeResidual(
                    navDataList = navList,
                    obsDataList = obsList,
                    posEcef = posEcef,
                    arrivalTowSec = arrivalTowSec,
                    weekNum = weekNum,
                )

                geometryMatrix = MathFn.calculateGeometryMatrix(
                    satPosEcefMeters = posAndRangeResidual.satPosMeters,
                    posEcef = posEcef,
                )

                val newWeightedGeometryMatrix = if (weightedGeometryMatrixInv == null) {
                    geometryMatrix
                } else {
                    val hMatrix = MathFn.calculateHMatrix(weightedGeometryMatrixInv, geometryMatrix)
                    hMatrix.mult(geometryMatrix.transpose()).mult(weightedGeometryMatrixInv)
                }

                deltaPosMeters = MathFn.matrixByColVecMult(newWeightedGeometryMatrix,
                                                               posAndRangeResidual.deltaRangeMeters)

                // Apply corrections to the position estimate
                posEcef[0] += deltaPosMeters[0]
                posEcef[1] += deltaPosMeters[1]
                posEcef[2] += deltaPosMeters[2]
                posEcef[3] += deltaPosMeters[3]

                iterCount += 1
                if (iterCount >= 100) {
                    Log.d("GNSS", "$deltaPosMeters")
                    Log.d("GNSS", "$posEcef")
                    throw RuntimeException("Least square iterations failed to converge")
                }
            }


        }
        // TODO: we assume we don't we to do least square again for now

        numSats = geometryMatrix.numRows()
        val rangeRate = SimpleMatrix(numSats, 1)
        val deltaRangeRate = SimpleMatrix(numSats, 1)
        val rangeRateWeight = SimpleMatrix(numSats, numSats)

        arrivalTowSec -= posEcef[3] / Const.c

        for (i in 0 until navList.size) {
            val navData = navList[i]
            val pseudorange = mutMeasurements[i].pseudorange

            val (gpsTowSec, newWeekNum) = MathFn.calculateCorrectedTransmitTowAndWeek(
                navData = navData,
                arrivalTowSec = arrivalTowSec,
                weekNum = weekNum,
                pseudorange = pseudorange,
            )

            val satPosAndVel = MathFn.calculateSatPosAndVel(
                navData = navData,
                arrivalTowSec = gpsTowSec,
                weekNum = newWeekNum,
                posEcef = posEcef,
            )

            val satClockCorrPlus = SatClockCorrection.fromNavAndGpsTime(
                navData = navData,
                arrivalTow = gpsTowSec + 0.5,
                weekNum = newWeekNum,
            )

            val satClockCorrMinus = SatClockCorrection.fromNavAndGpsTime(
                navData = navData,
                arrivalTow = gpsTowSec - 0.5,
                weekNum = newWeekNum,
            )

            val satClockErrRate = (satClockCorrPlus.satClockCorrectionMeters
                                   - satClockCorrMinus.satClockCorrectionMeters)

            // We can directly use `i` because we made len(nav_data_list) == len(obs_data_list)
            rangeRate[i, 0] = -1 * (
                    satPosAndVel.velX * geometryMatrix[i, 0]
                    + satPosAndVel.velY * geometryMatrix[i, 1]
                    + satPosAndVel.velZ * geometryMatrix[i, 2]
            )

            deltaRangeRate[i, 0] = (
                    mutMeasurements[i].inner.pseudorangeRateMps
                    - rangeRate[i, 0]
                    + satClockErrRate
                    - posEcef[7]
            )

            rangeRateWeight[i, i] = 1 / (mutMeasurements[i].uncertainty.pow(2))
        }

        val weightedGeoMatrix = rangeRateWeight.mult(geometryMatrix)
        val deltaRangeRateWeighted = rangeRateWeight.mult(deltaRangeRate)

        val qr = DecompositionFactory_DDRM.qr()
        qr.decompose(weightedGeoMatrix.ddrm)
        val q = qr.getQ(null, false)
        val r = qr.getR(null, false)
        val b = SimpleMatrix.wrap(q).transpose().mult(deltaRangeRateWeighted).ddrm
        val result = b.copy()
        TriangularSolver_DDRM.solveU(r.data, result.data, r.numCols)
        val velocity = SimpleMatrix.wrap(result)
        posEcef[4] = velocity[0, 0]
        posEcef[5] = velocity[1, 0]
        posEcef[6] = velocity[2, 0]
        posEcef[7] = velocity[3, 0]

        val rangeWeight = posAndRangeResidual.covarMatrixMetersSq.pseudoInverse()

        // Calculate position velocity uncertainty enu
        var velocityH = MathFn.calculateHMatrix(rangeRateWeight, geometryMatrix)
        var positionH = MathFn.calculateHMatrix(rangeWeight, geometryMatrix)

        val rotationMatrix = SimpleMatrix(4, 4)
        val llh = Xyz(posEcef[0], posEcef[1], posEcef[2]).toPhiLamH()
        val rotationSubMatrix = llh.rotationMatrix()

        for (i in 0 until 3) {
            for (j in 0 until 3) {
                rotationMatrix[i, j] = rotationSubMatrix[i, j]
            }
        }
        rotationMatrix[3, 3] = 1.0

        velocityH = rotationMatrix.mult(velocityH).mult(rotationMatrix.transpose())
        positionH = rotationMatrix.mult(positionH).mult(rotationMatrix.transpose())

        val pvUncertainty = doubleArrayOf(
            sqrt(positionH[0, 0]),
            sqrt(positionH[1, 1]),
            sqrt(positionH[2, 2]),
            sqrt(velocityH[0, 0]),
            sqrt(velocityH[1, 1]),
            sqrt(velocityH[2, 2]),
        )

        for (i in 0 until 6) {
            posUncertaintyEnu[i] = pvUncertainty[i]
        }
    }

//    private fun createSurveyDataButton() {
//        val index = surveyDataList.size - 1
//        val btn = Button(requireContext())
//        btn.height = 50
//        btn.width = 50
//        btn.text = "Survey Data $index"
//        btn.setOnClickListener { processGpsPosition(
//            index
//        ) }
//        frameSurveyData.addView(btn)
//    }

    private fun showDialogSuccessfullyResolvedLocation(ecefPos: Xyz) {
        val wgs84Pos = ecefPos.toPhiLamH()
        val phiDeg = wgs84Pos.phi / Math.PI * 180.0
        val lamDeg = wgs84Pos.lam / Math.PI * 180.0
        val hk80Pos = Hk1980.Grid.fromWgs84Xyz(ecefPos)
        val message = "The ecef location is $ecefPos\n" +
                "The wgs 84 pos is phi=$phiDeg, lam=$lamDeg, h=${wgs84Pos.h}\n" +
                "The HK1980 Grid pos is easting=${hk80Pos.e}, northing=${hk80Pos.n}, h=${hk80Pos.h}\n"
        AlertDialog.Builder(requireContext())
            .setTitle("Success")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    private fun computePseudoranges(obsDataList: List<ObsData>): List<ObsDataWithRange> {
//        var largestTowNs = Long.MIN_VALUE
//        for (obsData in obsDataList) {
//            if (obsData.txTimeNs > largestTowNs) {
//                largestTowNs = obsData.txTimeNs
//            }
//        }
//
//        return obsDataList.map { obsdata ->
//            val deltaI = largestTowNs - obsdata.txTimeNs
//            val pseudorange = (AVERAGE_TRAVEL_TIME_SECONDS + deltaI / 1e9) * Const.c
//
//            val snrLinear = 10.0.pow(obsdata.signalToNoiseRatioDb / 10.0)
//            val sigma = Const.c * GPS_CHIP_WIDTH_T_C_SEC * sqrt(
//                    GPS_CORRELATOR_SPACING_IN_CHIPS /
//                            (4 * GPS_DLL_AVERAGING_TIME_SEC * snrLinear))
//            ObsDataWithRange(
//                inner = obsdata,
//                pseudorange,
//                uncertainty = sigma)
//        }.toList()

        val WEEK_NS = 604800L * 1_000_000_000L

        return obsDataList.map { obsData ->
            val txTimeNs = obsData.txTimeNs + obsData.txTimeOffsetNs
            var deltaNs = obsData.rxTimeNs - txTimeNs
            if (deltaNs < 0) {
                deltaNs += WEEK_NS
            }
            val pseudorange = deltaNs * 1e-9 * Const.c

            val snrLinear = 10.0.pow(obsData.signalToNoiseRatioDb / 10.0)
            val sigma = Const.c * GPS_CHIP_WIDTH_T_C_SEC * sqrt(
                GPS_CORRELATOR_SPACING_IN_CHIPS /
                        (4 * GPS_DLL_AVERAGING_TIME_SEC * snrLinear)
            )

            ObsDataWithRange(
                inner = obsData,
                pseudorange = pseudorange,
                uncertainty = sigma,
            )
        }
//            .groupBy { it.inner.prn }
//            .map { (_, measurements) ->
//                val avgPseudorange = measurements
//                    .map { it.pseudorange }
//                    .average()
//
//                val avgUncertainty = measurements
//                    .map { it.uncertainty }
//                    .average()
//
//                val representative = measurements.maxByOrNull { it.inner.signalToNoiseRatioDb }
//                    ?: measurements.last()
//
//                ObsDataWithRange(
//                    inner = representative.inner,
//                    pseudorange = avgPseudorange,
//                    uncertainty = avgUncertainty,
//                )
//            }
//            .sortedBy { it.inner.prn }
    }

    private fun adjustPseudorange(obsDataList: List<ObsDataWithRange>, stationRtcmMap: Map<Int, Rtcm>) {
        for (obsData in obsDataList) {
            val prn = obsData.inner.prn
            val time = (obsData.inner.gpsTimeNs - obsData.inner.fullBiasNs - obsData.inner.biasNs) / 1e9  // Time of receiver receiving pseudorange

            var bestRtcm: Rtcm? = null
            var bestWeight = Double.NEGATIVE_INFINITY
            for (rtcm in stationRtcmMap.values) {
                val weight = rtcm.weightOfSat(prn, time) ?: continue
                if (weight > bestWeight) {  // w = 1/variance, the higher the better
                    bestRtcm = rtcm
                    bestWeight = weight
                }
            }

            // Skip if no PRN or not type 1
            val rtcm = bestRtcm ?: continue
            val dgps = rtcm.dgps[prn - 1] ?: continue
            val age = time - dgps.t0.asGpsTimeSecs()  // Time of base station

            val correctedPseudorange = obsData.pseudorange + dgps.prc + dgps.rrc * age
            obsData.pseudorange = correctedPseudorange
        }
    }


}