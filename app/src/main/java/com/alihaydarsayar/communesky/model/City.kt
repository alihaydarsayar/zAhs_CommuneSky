package com.alihaydarsayar.communesky.model

data class City(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        val Istanbul = City(name = "İstanbul", latitude = 41.0082, longitude = 28.9784)
    }
}
