package com.example.finalyear.core

data class PartialNavData (
    val prn: Int,
    var inner: NavData = NavData(prn),
    var subframesDecoded: Int = 0,
) {
    companion object {
        fun fromFullyDecoded(navData: NavData): PartialNavData {
            return PartialNavData(
                prn = navData.prn,
                inner = navData,
                subframesDecoded = 0b111,
            )
        }
    }

    fun subframeBit(subframeId: Int): Int {
        return 1 shl (subframeId - 1)
    }
    fun addSubframe(subframeId: Int) {
        val bit = subframeBit(subframeId)
        subframesDecoded = subframesDecoded or bit
    }
    fun hasDecodedSubframe(subframeId: Int): Boolean {
        val bit = subframeBit(subframeId)
        return (subframesDecoded and bit) == bit
    }
    fun isComplete(): Boolean {
        return hasDecodedSubframe(1) && hasDecodedSubframe(2) && hasDecodedSubframe(3)
    }
    fun isNew(): Boolean {
        return subframesDecoded == 0
    }
    fun isSubframeDecoded(prn: Int, subframeId: Int, issueOfData: Int): Boolean {
        if (prn != inner.prn) return false

        val hasDecodedSubframe = hasDecodedSubframe(subframeId)
        val issueOfDataMatches = when(subframeId) {
            1 -> inner.iodc == issueOfData
            2 -> inner.iode == issueOfData
            3 -> inner.iode == issueOfData
            // subframes 4 and 5 do no have IOD to match, so we assume they always match
            4 -> true
            5 -> true
            else -> throw IllegalArgumentException("Invalid subframe provided: $subframeId")
        }
        return hasDecodedSubframe && issueOfDataMatches
    }
    fun decodeStatus(prn: Int, subframeId: Int, issueOfData: Int): Status {
        if (prn != inner.prn) return Status.Continue

        val hasDecodedSubframe = hasDecodedSubframe(subframeId)
        val issueOfDataMatches = when(subframeId) {
            1 -> inner.iodc == issueOfData
            2 -> inner.iode == issueOfData
            3 -> inner.iode == issueOfData
            // subframes 4 and 5 do no have IOD to match, so we assume they always match
            4 -> true
            5 -> true
            else -> throw IllegalArgumentException("Invalid subframe provided: $subframeId")
        }
        if (hasDecodedSubframe) {
            return if (issueOfDataMatches) Status.Done else Status.Reset
        }
        return Status.Continue
    }
    enum class Status {
        Continue, Reset, Done;

        fun isDone(): Boolean {
            return this == Done
        }
        fun isReset(): Boolean {
            return this == Reset
        }
    }
}