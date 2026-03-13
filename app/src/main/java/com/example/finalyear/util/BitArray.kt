package com.example.finalyear.util

class BitArray(val buff: ByteArray) {
    fun getUnsigned(pos: Int, len: Int): Int {
        var bits = 0
        for (i in pos until pos + len) {
            bits = (bits shl 1) + (((buff[i / 8].toInt() and 0xff) shr (7 - i % 8)) and 1)
        }
        return bits
    }
    fun getSigned(pos: Int, len: Int): Int {
        val bits = getUnsigned(pos, len)

        if (len !in 1..31 || (bits and (1 shl (len - 1))) == 0) {
            return bits
        }
        return (bits - (1L shl len)).toInt()
    }
}