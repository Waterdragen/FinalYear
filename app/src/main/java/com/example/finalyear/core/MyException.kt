package com.example.finalyear.core

class MyException {
    class NotEnoughSatellites(s: String) : RuntimeException(s)
}