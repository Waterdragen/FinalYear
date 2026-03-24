package com.example.finalyear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.finalyear.core.HKNtripClient
import com.example.finalyear.core.NavData
import com.example.finalyear.core.ObsData
import com.example.finalyear.dgps.CustomRtcmParser
import com.example.finalyear.dgps.Rtcm
import com.example.finalyear.io.LogText
import com.example.finalyear.util.Parser
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDateTime

class PageSurvey : Fragment() {
    companion object {
        const val TOW_DECODED_MEASUREMENT_STATE_BIT = 3
    }

    val locManager: LocationManager by lazy {
        requireContext().getSystemService(LocationManager::class.java)!!
    }

    val gnssMeasurementsListener by lazy {
        object: GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent?) {
                handler.post { processGnssMeasurements(event )}
            }
        }
    }

    val gnssNavigationListener by lazy {
        object : GnssNavigationMessage.Callback() {
            override fun onGnssNavigationMessageReceived(msg: GnssNavigationMessage?) {
                handler.post { processGnssNavMsg(msg) }
            }
        }
    }

    val ntripClient = HKNtripClient()

    lateinit var handler: Handler
    lateinit var logNav: TextView
    lateinit var logObs: TextView
    lateinit var btnToggleSurvey: Button
    var decoder = Parser.GpsEphemerisDecoder()
    var logNavText = StringBuilder()
    var logObsText = StringBuilder(100000)
    var isSurveying = false
    var stationRtcmMap = mapOf<Int, Rtcm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler(Looper.getMainLooper())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.page_survey, container, false)
    }

    // We must store the widgets in this method
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logNav = view.findViewById(R.id.debugNavText)
        logObs = view.findViewById(R.id.debugObsText)
        btnToggleSurvey = view.findViewById(R.id.btnToggleSurvey)
        btnToggleSurvey.setOnClickListener { onBtnToggleSurveyClicked() }
    }

    override fun onStart() {
        super.onStart()

        val permission = checkPermission()
        Log.d("GNSS", "Permission: $permission")
        when (permission) {
            GnssPermission.NotGranted -> showPermissionRequiredDialog()
            GnssPermission.Denied -> showPermissionRequiredDialog()
            else -> {}
        }
    }

    override fun onStop() {
        super.onStop()
        locManager.unregisterGnssMeasurementsCallback(gnssMeasurementsListener)
        locManager.unregisterGnssNavigationMessageCallback(gnssNavigationListener)
    }

    private fun checkPermission(): GnssPermission {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return GnssPermission.NotGranted
        }
        try {
            val gpsEnabled = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            if (!gpsEnabled) return GnssPermission.GpsDisabled

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locManager.gnssCapabilities.hasNavigationMessages() &&
                locManager.gnssCapabilities.hasMeasurements()) {
                GnssPermission.Full
            } else {
                GnssPermission.GnssLackingCaps
            }
        } catch (e: SecurityException) {
            return GnssPermission.Denied
        }
    }

    private fun showPermissionRequiredDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Location permission required")
            .setMessage("This app needs location permission to receive GNSS measurements. Open app settings to grant permission.")
            .setCancelable(true)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun onBtnToggleSurveyClicked() {
        val permission = checkPermission()
        when (permission) {
            GnssPermission.NotGranted -> {
                showPermissionRequiredDialog()
                return
            }
            GnssPermission.Denied -> {
                showPermissionRequiredDialog()
                return
            }
            GnssPermission.GpsDisabled -> {
                // TODO: Handle fallback to android default positioning
                showPermissionRequiredDialog()
                Log.d("GNSS", "Gps is disabled")
                return
            }
            GnssPermission.GnssLackingCaps -> {
                // TODO: Handle fallback to android default positioning
                Log.d("GNSS", "GNSS lacking capabilities")
                return
            }
            GnssPermission.Full -> {}
        }

        if (!isSurveying) {
            // Start survey clicked
            try {
                // This never fails partially, as checked by GnssLackingCaps clause
                locManager.registerGnssMeasurementsCallback(gnssMeasurementsListener, handler)
                locManager.registerGnssNavigationMessageCallback(gnssNavigationListener, handler)

                btnToggleSurvey.setBackgroundColor(0xFFd3b88c.toInt())
                btnToggleSurvey.setTextColor(0xFF000000.toInt())
                btnToggleSurvey.text = "Stop surveying"

                ntripClient.startConnection()
                ntripClient.logText = logObsText

            } catch (e: SecurityException) {
                Log.e("GNSS", "Permission Denied")
                return
            }
        } else {
            // Stop survey clicked
            try {
                // This never fails partially, as checked by GnssLackingCaps clause
                locManager.unregisterGnssMeasurementsCallback(gnssMeasurementsListener)
                locManager.unregisterGnssNavigationMessageCallback(gnssNavigationListener)

                btnToggleSurvey.text = "Start surveying"
                btnToggleSurvey.setBackgroundColor(0xFF519872.toInt())
                btnToggleSurvey.setTextColor(0xFFffffff.toInt())

                stationRtcmMap = ntripClient.stopConnection()

                showSurveyFinishDialog()
                writeNavObsToCsv()
                clearMessage()
            } catch (e: SecurityException) {
                Log.e("GNSS", "Permission Denied")
                return
            } catch (e: Exception) {
                Log.e("GNSS", "Unknown error, most likely caused by unregistering without registering")
                return
            }
        }
        isSurveying = !isSurveying
    }

    private fun pushNavMessage(prn: Int, subframeId: Int) {
        logNavText.append("PRN: $prn, Subframe ID: $subframeId\n")
        logNav.text = logNavText
    }

    private fun pushNavMessageComplete(navData: NavData, currentCount: Int) {
        val prn = navData.prn
        val iode = navData.iode

        logNavText.append("PRN: $prn, NavData(prn=$prn, time=${navData.dateTime}, iode=$iode, satCount=$currentCount)\n")
        logNav.text = logNavText

        logObs.text = logObsText
    }

    private fun pushObsMessage(prn: Int, obsData: ObsData) {
//        val obsDateTime = GpsTime.fromNanos(obsData.gpsTimeNs).toDateTime()
//        logObsText.append("PRN: $prn, dateTime: $obsDateTime\n")
//        logObs.text = logObsText
    }

    private fun clearMessage() {
        logNavText.clear()
        logNav.text = logNavText
        logObsText.clear()
        logObs.text = logObsText
    }

    private fun processGnssMeasurements(event: GnssMeasurementsEvent?) {
        event ?: return

        decoder.pushMeasurements(event)
        for (obsDataQueue in decoder.obsDataList) {
            for (obsData in obsDataQueue) {
                pushObsMessage(obsData.prn, obsData)
            }
        }
    }

    private fun processGnssNavMsg(msg: GnssNavigationMessage?) {
        if (msg == null) {
            Log.w("GNSS", "No navigation message event")
            return
        }
        // We only decode GPS L1 for now
        if (msg.type != GnssNavigationMessage.TYPE_GPS_L1CA) return
        // Ensure no parity error
        if (msg.status != GnssNavigationMessage.STATUS_PARITY_PASSED) {
            Log.w("GNSS", "Skipping message with bad parity: ${msg.status}")
            return
        }

        val prn = msg.svid
        val messageType = msg.type shr 8
        val subMessageId = msg.submessageId
        val rawData = msg.data


        Log.d("GNSS", "Received: PRN: $prn, Subframe ID: $subMessageId")
        pushNavMessage(prn, subMessageId)

        // Push a new subframe to the decoder
        decoder.pushSubframe(prn, messageType, subMessageId, rawData)?.let { partialNavData ->
            // Check if subframe completes the prn
            if (partialNavData.isComplete()) {
                val navData = partialNavData.inner
                val currentCount = decoder.fullyDecodedNavDataCount()

                Log.d("GNSS", "Received or updated full nav message: PRN: ${partialNavData.prn}, NavData: $navData")
                pushNavMessageComplete(navData, currentCount)
            }
        }
    }

    fun writeNavObsToCsv() {
        // Apply ionospheric correction to all satellites
        decoder.setIonoCorrections()

        val dt = LocalDateTime.now()
        val df = DecimalFormat("00")
        val year = dt.year
        val month = df.format(dt.month.value.toLong())
        val day = df.format(dt.dayOfMonth.toLong())
        val hour = df.format(dt.hour.toLong())
        val minute = df.format(dt.minute.toLong())
        val second = df.format(dt.second.toLong())

        val filesDir = requireContext().filesDir

        val fileNameBase = "$year-$month-$day-$hour$minute$second"
        val navFileName = "$fileNameBase-nav.csv"
        val navFile = File(filesDir, navFileName)
        NavData.writeCsvHeader(navFile)

        for (partialNavData in decoder.partialNavDataList) {
            if (!partialNavData.isComplete()) continue

            partialNavData.inner.writeCsvRow(navFile)
        }

        val obsFileName = "$fileNameBase-obs.csv"
        val obsFile = File(filesDir, obsFileName)
        ObsData.writeCsvHeader(obsFile)

        for (obsDataQueue in decoder.obsDataList) {
            for (obsData in obsDataQueue) {
                obsData.writeCsvRow(obsFile)
            }
        }
        decoder.clearData()

        // Write DGNSS to text file
        val dgnssText = CustomRtcmParser.serializeStationRtcmMap(stationRtcmMap)
        LogText().save(dgnssText.encodeToByteArray(), "$fileNameBase-dgnss.txt")
    }

    private fun showSurveyFinishDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Survey finished")
            .setMessage("Successfully saved as csv files to internal storage.")
            .setCancelable(true)
            .setPositiveButton("Ok") { _, _ -> }
            .show()
    }

    fun getConstellationName(type: Int): String {
        return when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
            GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
            else -> "Unknown"
        }
    }

    enum class GnssPermission {
        NotGranted, Denied, GpsDisabled, GnssLackingCaps, Full,
    }
}