package com.example.finalyear.core

class MyException {
    class NotEnoughSatellites(s: String) : RuntimeException(s) {
        companion object {
            fun fromCount(count: Int): NotEnoughSatellites {
                return NotEnoughSatellites("Expected 4 satellites, got $count")
            }
        }
    }
    class NotEnoughObservations : RuntimeException("Need at least 4 observations from any same epoch")
    class LsConvergeFail(s: String) : RuntimeException(s)
}