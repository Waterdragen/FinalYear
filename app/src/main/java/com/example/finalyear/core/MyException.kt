package com.example.finalyear.core

import java.io.File

class MyException {
    class NotEnoughSatellites(s: String) : RuntimeException(s) {
        companion object {
            fun fromCount(count: Int): NotEnoughSatellites {
                return NotEnoughSatellites("Expected 4 satellites, got $count")
            }
        }
    }
    class FileNotFound(file: File) : RuntimeException("File does not exist: ${file.name}")
    class CsvParseError(file: File) : RuntimeException("CSV parse error in file: ${file.name}")
    class JsonParseError(file: File) : RuntimeException("JSON parse error in file: ${file.name}")
    class NotEnoughObservations : RuntimeException("Need at least 4 observations from any same epoch")
    class LsConvergeFail(s: String) : RuntimeException(s)
}