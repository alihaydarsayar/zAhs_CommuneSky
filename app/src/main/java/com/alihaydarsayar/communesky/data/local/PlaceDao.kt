package com.alihaydarsayar.communesky.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaceDao {

    @Query("SELECT * FROM places ORDER BY sortOrder, id")
    abstract fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places ORDER BY sortOrder, id")
    abstract suspend fun getAll(): List<PlaceEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM places")
    abstract suspend fun maxSortOrder(): Int

    @Insert
    abstract suspend fun insert(place: PlaceEntity): Long

    @Query("DELETE FROM places WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE places SET sortOrder = :sortOrder WHERE id = :id")
    protected abstract suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Tek bir SQL cümlesi: seçilen yer "Ev" olur, diğerlerinin işareti kalkar. En fazla bir Ev olur. */
    @Query("UPDATE places SET isHome = (id = :id)")
    abstract suspend fun setHome(id: Long)

    @Query("UPDATE places SET isHome = 0")
    abstract suspend fun clearHome()

    /** Sürükleyerek sıralama sonrası yeni sırayı yazar; hepsi tek seferde (yarım kalmaz). */
    @Transaction
    open suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
