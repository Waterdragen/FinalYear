package com.example.finalyear.io

import com.example.finalyear.dgps.Rtcm
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import java.util.Base64

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

fun String.deserializeToRtcmMap(): Map<Int, Rtcm> =
    rtcmJson.decodeFromString(this)
