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

private val rtcmJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = false
    prettyPrint = true
}
fun Map<Int, Rtcm>.serialize(): String =
    rtcmJson.encodeToString(this)

