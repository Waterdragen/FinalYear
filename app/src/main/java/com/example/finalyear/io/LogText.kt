package com.example.finalyear.io

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

class LogText {
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