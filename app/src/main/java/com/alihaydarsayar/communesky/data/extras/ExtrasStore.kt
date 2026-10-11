package com.alihaydarsayar.communesky.data.extras

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.alihaydarsayar.communesky.model.PlaceExtras
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Bir yerin ek verileri ([PlaceExtras]), JSON olarak. Yer silinince satırı da silinir. */
@Entity(tableName = "place_extras")
data class PlaceExtrasEntity(
    @PrimaryKey val placeId: Long,
    val extrasJson: String,
    val updatedAtMillis: Long,
)

@Dao
interface PlaceExtrasDao {
    @Query("SELECT * FROM place_extras")
    fun observeAll(): Flow<List<PlaceExtrasEntity>>

    @Query("SELECT * FROM place_extras WHERE placeId = :placeId")
    suspend fun get(placeId: Long): PlaceExtrasEntity?

    @Upsert
    suspend fun upsert(entity: PlaceExtrasEntity)

    @Query("DELETE FROM place_extras WHERE placeId = :placeId")
    suspend fun delete(placeId: Long)

    @Query("DELETE FROM place_extras WHERE placeId NOT IN (:placeIds)")
    suspend fun deleteExcept(placeIds: List<Long>)
}

/** Ek verilerin cihazda saklandığı yer. */
@Singleton
class ExtrasStore @Inject constructor(
    private val dao: PlaceExtrasDao,
    private val json: Json,
) {
    fun observeAll(): Flow<Map<Long, PlaceExtras>> = dao.observeAll()
        .map { rows -> rows.mapNotNull { row -> decode(row)?.let { row.placeId to it } }.toMap() }
        .distinctUntilChanged()

    suspend fun get(placeId: Long): PlaceExtras? = dao.get(placeId)?.let(::decode)

    suspend fun save(placeId: Long, extras: PlaceExtras) {
        dao.upsert(PlaceExtrasEntity(placeId, json.encodeToString(PlaceExtras.serializer(), extras), System.currentTimeMillis()))
    }

    suspend fun delete(placeId: Long) = dao.delete(placeId)

    /** Silinmiş yerlerin ek verilerini temizler. */
    suspend fun deleteExcept(placeIds: List<Long>) = dao.deleteExcept(placeIds)

    // Uygulama güncellemesiyle biçim uyumsuz hale gelirse çökmek yerine veri yok sayılır; yeniden indirilir.
    private fun decode(row: PlaceExtrasEntity): PlaceExtras? =
        runCatching { json.decodeFromString(PlaceExtras.serializer(), row.extrasJson) }.getOrNull()
}
