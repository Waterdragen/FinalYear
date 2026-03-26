package com.example.finalyear.io

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class LogText {
    fun saveWithTimestamp(byteArray: ByteArray): String? {
        val dt = LocalDateTime.now()
        val fileName = "%d-%02d-%02d-%02d%02d%02d".format(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute, dt.second)
        return save(byteArray, fileName)
    }

    fun save(byteArray: ByteArray, fileName: String): String? {
        return try {
            val text = String(byteArray, StandardCharsets.UTF_8)

            val file = File("/data/data/com.example.finalyear/files/", fileName)
            file.writeText(text)
            file.absolutePath

        } catch (e: Exception) {
            Log.e("GNSS", e.stackTraceToString())
            null
        }
    }

    fun saveText(text: String, fileName: String): String? {
        return try {
            val file = File("/data/data/com.example.finalyear/files/", fileName)
            file.writeText(text)
            file.absolutePath

        } catch (e: Exception) {
            Log.e("GNSS", e.stackTraceToString())
            null
        }
    }
}