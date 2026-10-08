package com.alihaydarsayar.communesky.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room bu arayüzden SQL çalıştıran kodu üretir. */
@Dao
interface WeatherCacheDao {

    /** Tablo her değiştiğinde tüm yerlerin önbelleğini yayar; ekran bunu dinleyerek kendini günceller. */
    @Query("SELECT * FROM weather_cache")
    fun observeAll(): Flow<List<WeatherCacheEntity>>

    @Query("SELECT * FROM weather_cache WHERE id = :id")
    suspend fun get(id: Long): WeatherCacheEntity?

    @Upsert
    suspend fun upsertAll(entities: List<WeatherCacheEntity>)

    @Query("DELETE FROM weather_cache WHERE id = :id")
    suspend fun delete(id: Long)
}
