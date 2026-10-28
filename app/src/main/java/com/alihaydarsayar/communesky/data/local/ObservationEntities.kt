package com.alihaydarsayar.communesky.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Bir yerin son istasyon ölçümü. [observationJson] null ise son istekte işe yarar ölçüm
 * bulunamamıştır (yine de [requestedAtMillis] saklanır; 3 dakika içinde tekrar sorulmaz).
 */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val placeId: Long,
    val observationJson: String?,
    val requestedAtMillis: Long,
)

/**
 * Bir yer için bulunan MGM istasyonu. Yer başına bir kez sorulur; "Bulunduğum yer" 2 km'den
 * fazla değişirse yeniden bulunur.
 */
@Entity(tableName = "mgm_stations")
data class MgmStationEntity(
    @PrimaryKey val placeId: Long,
    /** İstasyonu bulurken kullanılan yerin koordinatı. */
    val queryLatitude: Double,
    val queryLongitude: Double,
    /** Bu yer için MGM istasyonu yoksa null. */
    val stationId: Int?,
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
    val resolvedAtMillis: Long,
    /** Yere en yakın istasyonlar (en yakından uzağa), JSON. Ölçüm alırken sırayla denenir. */
    val candidatesJson: String? = null,
    /** Listeyi bulan uygulamanın sürüm kodu; sürüm değişince liste yeniden bulunur. */
    @ColumnInfo(defaultValue = "0") val appVersion: Int = 0,
    /** Liste MGM'nin bütün istasyon listesinden mi çıkarıldı (false: yalnız yerin ili, eksik olabilir). */
    @ColumnInfo(defaultValue = "1") val complete: Boolean = true,
)

@Dao
interface ObservationDao {
    @Query("SELECT * FROM observations")
    fun observeAll(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE placeId = :placeId")
    suspend fun get(placeId: Long): ObservationEntity?

    @Upsert
    suspend fun upsert(entity: ObservationEntity)

    @Query("SELECT * FROM mgm_stations WHERE placeId = :placeId")
    suspend fun station(placeId: Long): MgmStationEntity?

    @Upsert
    suspend fun upsertStation(entity: MgmStationEntity)

    @Query("DELETE FROM observations WHERE placeId = :placeId")
    suspend fun deleteObservation(placeId: Long)

    @Query("DELETE FROM mgm_stations WHERE placeId = :placeId")
    suspend fun deleteStation(placeId: Long)
}
