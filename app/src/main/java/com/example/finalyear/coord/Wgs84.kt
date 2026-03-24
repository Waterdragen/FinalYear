package com.example.finalyear.coord

import kotlin.math.sqrt

object Wgs84: Ellipsoid {
    override val a = 6_378_137.0
    override val ecc2 = 6.69437999e-3
    override val b = sqrt(a * a * (1 - ecc2))
    override val eccPrime2 = (a * a - b * b) / (b * b)
}