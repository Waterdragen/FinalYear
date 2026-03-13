package com.example.finalyear

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        println("${GpsTime.fromNanos(1457459210001000000).toDateTime()}")

        assertEquals(4, 2 + 2)
    }
}