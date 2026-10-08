package com.alihaydarsayar.communesky.data.places

import androidx.room.withTransaction
import com.alihaydarsayar.communesky.data.local.PlaceEntity
import com.alihaydarsayar.communesky.data.local.WeatherDatabase
import com.alihaydarsayar.communesky.data.search.SearchResultMapping.isSamePlaceAs
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import com.alihaydarsayar.communesky.model.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Kullanıcının kaydettiği yerler: ekleme, silme, sıralama ve "Ev" işareti. */
@Singleton
class PlacesRepository @Inject constructor(
    private val database: WeatherDatabase,
) {
    private val placeDao = database.placeDao()
    private val cacheDao = database.weatherCacheDao()

    val places: Flow<List<SavedPlace>> = placeDao.observeAll()
        .map { list -> list.map { it.toModel() } }
        .distinctUntilChanged()

    suspend fun all(): List<SavedPlace> = placeDao.getAll().map { it.toModel() }

    /**
     * Yeri listenin sonuna ekler ve kimliğini döndürür. Aynı yer zaten kayıtlıysa yenisini
     * eklemez, kayıtlı olanın kimliğini döndürür.
     */
    suspend fun add(result: PlaceSearchResult): Long = database.withTransaction {
        val existing = placeDao.getAll().firstOrNull { it.toSearchResult().isSamePlaceAs(result) }
        existing?.id ?: placeDao.insert(
            PlaceEntity(
                name = result.name,
                region = result.region,
                latitude = result.latitude,
                longitude = result.longitude,
                sortOrder = placeDao.maxSortOrder() + 1,
            ),
        )
    }

    /** Yeri ve önbelleğini siler. Geri almak için silinen yeri döndürür. */
    suspend fun remove(id: Long): SavedPlace? = database.withTransaction {
        val removed = placeDao.getAll().firstOrNull { it.id == id }
        placeDao.delete(id)
        cacheDao.delete(id)
        removed?.toModel()
    }

    /** "Geri al": silinen yeri aynı kimlik ve sırayla geri koyar. */
    suspend fun restore(place: SavedPlace) {
        database.withTransaction {
            placeDao.insert(
                PlaceEntity(
                    id = place.id,
                    name = place.name,
                    region = place.region,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    sortOrder = place.sortOrder,
                    isHome = false,
                ),
            )
            if (place.isHome) placeDao.setHome(place.id)
        }
    }

    suspend fun reorder(ids: List<Long>) = placeDao.reorder(ids)

    /** [id] null ise Ev işareti kaldırılır. Her durumda en fazla bir yer Ev olur. */
    suspend fun setHome(id: Long?) {
        if (id == null) placeDao.clearHome() else placeDao.setHome(id)
    }

    private fun PlaceEntity.toModel() = SavedPlace(
        id = id,
        name = name,
        region = region,
        latitude = latitude,
        longitude = longitude,
        isHome = isHome,
        sortOrder = sortOrder,
    )

    private fun PlaceEntity.toSearchResult() = PlaceSearchResult(
        name = name,
        region = region,
        countryCode = null,
        latitude = latitude,
        longitude = longitude,
    )
}
