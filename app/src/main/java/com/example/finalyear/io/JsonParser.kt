package com.example.finalyear.io

import com.example.finalyear.core.MyException
import com.example.finalyear.dgps.Rtcm
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

object JsonParser {
    fun tryParseDgnss(file: File): Map<Int, Rtcm> {
        if (!file.exists()) {
            throw MyException.FileNotFound(file)
        }
        val text = file.readText(Charsets.UTF_8)
        return try {
            rtcmJson.decodeFromString(text)
        } catch (e: Exception) {
            throw MyException.JsonParseError(file)
        }
    }

    fun serializeDgnss(stationRtcmMap: Map<Int, Rtcm>): String {
        return rtcmJson.encodeToString(stationRtcmMap)
    }

    private val rtcmJson = Json {
        ignoreUnknownKeys = false
        encodeDefaults = false
        prettyPrint = true
    }

    object ByteArrayBase64Serializer : KSerializer<ByteArray> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("ByteArrayBase64", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ByteArray) {
            val encoded = Base64.getEncoder().encodeToString(value)
            encoder.encodeString(encoded)
        }

        override fun deserialize(decoder: Decoder): ByteArray {
            val encoded = decoder.decodeString()
            return Base64.getDecoder().decode(encoded)
        }
    }
}



