package com.example.finalyear.util

class BitArray(val buff: ByteArray) {
    fun getUnsigned(pos: Int, len: Int): Int {
        var bits = 0
        for (i in pos until pos + len) {
            val targetByte = buff[i / 8].toInt() and 0xff
            val bitIndex = i % 8                  // counting from the left
            val bitIndexFromRight = 7 - bitIndex  // counting from the right
            val extractedBit = (targetByte shr bitIndexFromRight) and 1  // move bit to the right, set rest to 0 except bit
            bits = (bits shl 1) or extractedBit  // push old bits to the left, include the new bit on the right
        }
        return bits
    }
    fun getSigned(pos: Int, len: Int): Int {
        val unsignedBits = getUnsigned(pos, len)

        // Extract the sign bit (highest bit)
        val signBitMask = 1 shl (len - 1)
        val signBitSet = (unsignedBits and signBitMask) != 0

        // Sign bit set -> negative (convert from two's complement)
        if (signBitSet) {
            return (unsignedBits - (1L shl len)).toInt()
        }
        return unsignedBits  // the value is positive
    }
}