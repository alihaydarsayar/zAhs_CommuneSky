package com.alihaydarsayar.communesky.data.observation

import com.alihaydarsayar.communesky.data.local.MgmStationEntity
import com.alihaydarsayar.communesky.data.local.ObservationDao
import com.alihaydarsayar.communesky.data.local.ObservationEntity
import com.alihaydarsayar.communesky.model.Observation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Ölçümleri Room'da saklar. Ölçüm küçük olduğu için JSON olarak tek sütunda durur. */
class RoomObservationStore @Inject constructor(
    private val dao: ObservationDao,
    private val json: Json,
) : ObservationStore {

    override fun observeAll(): Flow<Map<Long, Observation>> = dao.observeAll()
        .map { list -> list.mapNotNull { e -> e.decode()?.let { e.placeId to it } }.toMap() }
        .distinctUntilChanged()

    override suspend fun get(placeId: Long): Observation? = dao.get(placeId)?.decode()

    override suspend fun lastRequestMillis(placeId: Long): Long? = dao.get(placeId)?.requestedAtMillis

    override suspend fun save(placeId: Long, observation: Observation?, requestedAtMillis: Long) {
        dao.upsert(
            ObservationEntity(
                placeId = placeId,
                observationJson = observation?.let { json.encodeToString(Observation.serializer(), it) },
                requestedAtMillis = requestedAtMillis,
            ),
        )
    }

    override suspend fun station(placeId: Long): MgmStationInfo? = dao.station(placeId)?.let {
        MgmStationInfo(it.queryLatitude, it.queryLongitude, it.stationId, it.name, it.latitude, it.longitude, it.elevation)
    }

    override suspend fun saveStation(placeId: Long, station: MgmStationInfo) {
        dao.upsertStation(
            MgmStationEntity(
                placeId = placeId,
                queryLatitude = station.queryLatitude,
                queryLongitude = station.queryLongitude,
                stationId = station.stationId,
                name = station.name,
                latitude = station.latitude,
                longitude = station.longitude,
                elevation = station.elevation,
                resolvedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun ObservationEntity.decode(): Observation? = observationJson?.let {
        runCatching { json.decodeFromString(Observation.serializer(), it) }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ObservationModule {
    @Binds abstract fun bindStore(impl: RoomObservationStore): ObservationStore
}
