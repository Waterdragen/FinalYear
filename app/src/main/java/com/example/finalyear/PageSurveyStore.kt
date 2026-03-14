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
    lateinit var textSppResult: TextView
    lateinit var textDgnssResult: TextView
    lateinit var frameSurveyData: LinearLayout

//    val surveyDataList: ArrayList<SurveyData> = arrayListOf()
    var surveyDataDgnss: SurveyData? = null
    var surveyDataSpp: SurveyData? = null

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
        textSppResult = view.findViewById(R.id.textSppResult)
        textDgnssResult = view.findViewById(R.id.textDgnssResult)

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

        val obsDataListSpp = computePseudoranges(obsDataList)
        val obsDataListDgnss = adjustPseudorange(obsDataListSpp, stationRtcmMapOptional)

        surveyDataSpp = SurveyData.new(baseName, navDataList, obsDataListSpp)
        val newSurveyData = SurveyData.new(baseName, navDataList, obsDataListDgnss)
        surveyDataDgnss = newSurveyData
        showDialog("Success", "Stored `$baseName` to survey data list")

        textLoadedFile.text = "Loaded file: ${navFile.name}"
        textNumNavSat.text = "Navigation satellite count: ${newSurveyData.navDataList.size}"
        textNumObs.text = "Number of observations: ${obsDataListSpp.size}"  // Number of raw measurements
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

        // Approximate location of Hong Kong
        val APPROX_X = -2.416e6
        val APPROX_Y = 5.388e6
        val APPROX_Z = 2.410e6

        for (epoch in surveyDataSpp.obsDataListByEpoch.keys) {
            val sppObsList = surveyDataSpp.obsDataListByEpoch[epoch]!!
            val dgnssObsList = surveyDataDgnss.obsDataListByEpoch[epoch]!!
            val sppPosEcef = SimpleMatrix(arrayOf(doubleArrayOf(APPROX_X, APPROX_Y, APPROX_Z, 0.0, 0.0, 0.0, 0.0, 0.0)))
            val sppPosUncertaintyEnu = SimpleMatrix(6, 1)
            val dgnssPosEcef = SimpleMatrix(arrayOf(doubleArrayOf(APPROX_X, APPROX_Y, APPROX_Z, 0.0, 0.0, 0.0, 0.0, 0.0)))
            val dgnssPosUncertaintyEnu = SimpleMatrix(6, 1)

            try {
                leastSquareAdjustment(sppNavList, sppObsList, sppPosEcef, sppPosUncertaintyEnu)
                leastSquareAdjustment(dgnssNavList, dgnssObsList, dgnssPosEcef, dgnssPosUncertaintyEnu)

                sppPosList.add(Hk1980.Grid.fromWgs84Xyz(Xyz.from(sppPosEcef)))
                dgnssPosList.add(Hk1980.Grid.fromWgs84Xyz(Xyz.from(dgnssPosEcef)))
            } catch (e: MyException.NotEnoughSatellites) {
                showDialog("Not Enough satellites", e.toString())
                return
            } catch (e: MyException.LsConvergeFail) {
                continue
            }
        }

        val sppAvgPos = arrayOf(0.0, 0.0, 0.0)
        var dgnssAvgPos = arrayOf(0.0, 0.0, 0.0)
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

        handleSuccessfullyResolvedLocation(
            Hk1980.Grid(sppAvgPos[0], sppAvgPos[1], sppAvgPos[2]),
            Hk1980.Grid(dgnssAvgPos[0], dgnssAvgPos[1], dgnssAvgPos[2]),
        )
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

        var iterCount = 0
        var delta = SimpleMatrix(4, 1).also {
            it[0] = 100.0; it[1] = 100.0; it[2] = 100.0; it[3] = 100.0
        }  // large initial value to force first iteration

        var weightedGeometryMatrixInv: SimpleMatrix? = null

        while (abs(delta[0]) + abs(delta[1]) + abs(delta[2]) >= 1e-7) {
            iterCount++

            if (iterCount > 50) {
                Log.d("GNSS", "Last delta: $delta")
                Log.d("GNSS", "Final posEcef: $posEcef")
                throw MyException.LsConvergeFail("Least squares failed to converge after $iterCount iterations")
            }

            val approxRxTow = arrivalTowSec - posEcef[3] / Const.c

            posAndRangeResidual = MathFn.calculateSatPosAndPseudorangeResidual(
                navDataList = navList,
                obsDataList = obsList,
                posEcef = posEcef,
                arrivalTowSec = approxRxTow,
                weekNum = weekNum
            )

            val satPosEcefMeters = posAndRangeResidual.satPosMeters

            // Geometry / design matrix (unit vectors + clock column)
            geometryMatrix = MathFn.calculateGeometryMatrix(
                satPosEcefMeters = satPosEcefMeters,
                posEcef = posEcef
            )

            // Weighted matrix (covariance matrix squared)
            val covarMatrixMetersSq = posAndRangeResidual.covarMatrixMetersSq
            val det = covarMatrixMetersSq.determinant()

            val weightedGeometryMatrix = if (det < 1e-9) {
                geometryMatrix
            } else {
                if (weightedGeometryMatrixInv == null || iterCount == 1) {
                    weightedGeometryMatrixInv = covarMatrixMetersSq.invert()
                }
                val hMatrix = MathFn.calculateHMatrix(weightedGeometryMatrixInv!!, geometryMatrix)  // !! overwritten by previous condition
                hMatrix.mult(geometryMatrix.transpose()).mult(weightedGeometryMatrixInv!!)
            }

            // Compute adjustments
            delta = MathFn.matrixByColVecMult(weightedGeometryMatrix, posAndRangeResidual.deltaRangeMeters)

            // Apply adjustments
            posEcef[0] += delta[0]
            posEcef[1] += delta[1]
            posEcef[2] += delta[2]
            posEcef[3] += delta[3]
        }
        Log.d("GNSS", "posEcef[3]: ${posEcef[3]}")

        numSats = geometryMatrix.numRows()
        val rangeRate = SimpleMatrix(numSats, 1)
        val deltaRangeRate = SimpleMatrix(numSats, 1)
        val rangeRateWeight = SimpleMatrix(numSats, numSats)

//        arrivalTowSec -= posEcef[3] / Const.c  // old
        val approxRxTow = arrivalTowSec - posEcef[3] / Const.c // new

        for (i in navList.indices) {
            val navData = navList[i]
            val pseudorange = mutMeasurements[i].pseudorange

            val (gpsTowSec, newWeekNum) = MathFn.calculateCorrectedTransmitTowAndWeek(
                navData = navData,
                arrivalTowSec = approxRxTow,
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

    private fun adjustPseudorange(obsDataList: List<ObsDataWithRange>, stationRtcmMap: Map<Int, Rtcm>): List<ObsDataWithRange> {
        val adjustedObsDataList = arrayListOf<ObsDataWithRange>()

        for (unadjustedObsData in obsDataList) {
            val obsData = unadjustedObsData.clone()

            val prn = obsData.inner.prn
            val time = obsData.inner.gpsTimeNs.toDouble() / 1e9  // Time of receiver receiving pseudorange

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

            val correctedPseudorange = obsData.pseudorange - (dgps.prc + dgps.rrc * age)
            obsData.pseudorange = correctedPseudorange

            adjustedObsDataList.add(obsData)  // Push cloned to new list
        }
        return adjustedObsDataList
    }


}