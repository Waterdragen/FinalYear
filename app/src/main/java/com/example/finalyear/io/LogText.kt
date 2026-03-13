package com.example.finalyear.io

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.time.LocalDateTime

class LogText {
    fun saveWithTimestamp(byteArray: ByteArray): String? {
        val dt = LocalDateTime.now()
        val df = DecimalFormat("00")
        val year = dt.year
        val month = df.format(dt.month.value.toLong())
        val day = df.format(dt.dayOfMonth.toLong())
        val hour = df.format(dt.hour.toLong())
        val minute = df.format(dt.minute.toLong())
        val second = df.format(dt.second.toLong())

        val fileName = "$year-$month-$day-$hour$minute$second.txt"

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
}