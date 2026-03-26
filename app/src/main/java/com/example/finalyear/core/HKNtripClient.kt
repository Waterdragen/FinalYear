package com.example.finalyear.core

import android.util.Log
import com.example.finalyear.dgps.Rtcm
import com.example.finalyear.dgps.RtcmDecoder
import com.example.finalyear.math.GpsTime
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Socket
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class HKNtripClient {
    companion object {
        const val HOST = "www.geodetic.gov.hk"
        const val PORT = 2101
        const val MOUNT_POINT = "DGPS"
        const val USERNAME = "YipChikHim"
        const val PASSWORD = "vV3F31"
    }

    var logText: StringBuffer? = null
    var numDgnss: AtomicInteger? = null

    private var socket: Socket? = null
    private var inputStream: BufferedInputStream? = null
    private var outputStream: OutputStream? = null
    private var isRunning = false
    private var stationRtcmMap = mutableMapOf<Int, Rtcm>()  // maps stationId to the latest Rtcm message (might change to RtcmMap of different types of Rtcm)

    fun startConnection() {
        Thread {
            try {
                connectAndStream()
            } catch (e: Exception) {
                Log.e("GNSS", "Connection error: ${e.message}")
            }
        }.start()
    }

    private fun connectAndStream() {
        try {
            socket = Socket(HOST, PORT)
            outputStream = socket?.getOutputStream()
            inputStream = BufferedInputStream(socket?.getInputStream())

            // Prepare  basic auth
            val auth = "$USERNAME:$PASSWORD"
            val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
            val request = "GET /$MOUNT_POINT HTTP/1.0\r\n" +
                    "User-Agent: NTRIP CustomClient/1.0\r\n" +
                    "Authorization: Basic $encodedAuth\r\n" +
                    "Connection: close\r\n\r\n"

            outputStream?.write(request.toByteArray())
            outputStream?.flush()

            val response = StringBuilder()
            var line: String?

            while (true) {
                line = readLine()
                if (line.isNullOrEmpty()) break
                response.append(line).append('\n')
            }
            Log.d("GNSS", "HTTP Response: $response")
            if (!response.contains("ICY 200 OK")) {
                Log.e("GNSS", "Connection failed: $response")
                return
            }

            // Reading binary RTCM stream
            isRunning = true
            val buffer = ByteArray(1024)
            var bytesRead: Int

            while (isRunning) {
                bytesRead = inputStream?.read(buffer) ?: break
                // Immediately record the receiver time
                val recvTime = GpsTime.fromUnixNow()

                // Append to messageBuffer and parse RTCM messages
                val message = buffer.copyOf(bytesRead)

                // Try to extract and parse the next RTCM message
                val rtcm = RtcmDecoder.decodeRtcm(message) ?: continue
                val stationId = rtcm.stationId
                Log.d("GNSS", "Received DGNSS message type ${rtcm.msgType} from station $stationId!")
                rtcm.recvTime = recvTime
                pushDgnssMessage(rtcm)
                stationRtcmMap[stationId] = rtcm  // Only stores the latest DGNSS (storage and complexity concerns, older measurements cannot be effectively adjusted)
            }

        } catch (e: Exception) {
            if (e.message == "Socket closed") {
                Log.w("GNSS", "Stream error: ${e.message}")
            } else {
                Log.e("GNSS", "Stream error: ${e.message}")
            }
        } finally {
            stopConnection()
        }
    }

    private fun readLine(): String? {
        val sb = StringBuilder()
        var byte: Int
        while (true) {
            byte = inputStream?.read() ?: -1
            if (byte == -1 || byte == '\n'.code) break
            if (byte != '\r'.code) sb.append(byte.toChar())
        }
        return if (byte == -1 && sb.isEmpty()) null else sb.toString()
    }

    fun stopConnection(): Map<Int, Rtcm> {
        isRunning = false
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            Log.e("GNSS", "Stop error: ${e.message}")
        }
        return stationRtcmMap
    }

    private fun pushDgnssMessage(rtcm: Rtcm) {
        logText?.append("DGNSS Type ${rtcm.msgType} station id: ${rtcm.stationId}\n")
        var num = 0
        for (_rtcm in stationRtcmMap.values) {
            num += _rtcm.numDgnss()
        }
        numDgnss?.set(num)
    }
}