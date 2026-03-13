package com.example.finalyear.dgps

import com.example.finalyear.util.GTime

data class DgpsCorrection(
    val t0: GTime,
    val prc: Double,
    val rrc: Double,
    val iod: Int,
    val udre: Int
) {
    companion object {
        fun deserialize(str: String): DgpsCorrection? {
            // Remove outer parentheses
            val clean = str.trim().removeSurrounding("(", ")").trim()

            // We need to parse nested structure → better to find the first GTime part
            // Look for the first balanced (....) pair
            var depth = 0
            var gtimeEnd = -1

            for (i in clean.indices) {
                when (clean[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            gtimeEnd = i
                            break
                        }
                    }
                }
            }

            if (gtimeEnd == -1) return null

            val gtimePart = clean.substring(0, gtimeEnd + 1).trim()
            val rest = clean.substring(gtimeEnd + 1).trim()

            // rest should now start with comma
            if (!rest.startsWith(",")) return null
            val valueParts = rest.substring(1).split(",").map { it.trim() }

            if (valueParts.size != 4) return null

            val t0 = GTime.deserialize(gtimePart) ?: return null

            return try {
                DgpsCorrection(
                    t0 = t0,
                    prc = valueParts[0].toDoubleOrNull() ?: return null,
                    rrc = valueParts[1].toDoubleOrNull() ?: return null,
                    iod = valueParts[2].toIntOrNull() ?: return null,
                    udre = valueParts[3].toIntOrNull() ?: return null
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun serialize(): String {
        return "(${t0.serialize()}, $prc, $rrc, $iod, $udre)"
    }
}