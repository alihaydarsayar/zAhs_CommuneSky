package com.alihaydarsayar.communesky.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WeatherCacheEntity::class], version = 1)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherCacheDao(): WeatherCacheDao
}
