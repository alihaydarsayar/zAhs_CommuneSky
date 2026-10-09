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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Ölçümleri Room'da saklar. Ölçüm küçük olduğu için JSON olarak tek sütunda durur. */
class RoomObservationStore @Inject constructor(
    private val dao: ObservationDao,
    private val json: Json,
) : ObservationStore {

    private val candidatesSerializer = ListSerializer(MgmCandidate.serializer())

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

    override suspend fun station(placeId: Long): MgmStationInfo? = dao.station(placeId)?.let { entity ->
        // 1.2'nin ilk test sürümleri tek istasyon saklıyordu; aday listesi yoksa yeniden bulunur.
        val candidates = entity.candidatesJson
            ?.let { runCatching { json.decodeFromString(candidatesSerializer, it) }.getOrNull() }
            .orEmpty()
        MgmStationInfo(entity.queryLatitude, entity.queryLongitude, candidates)
    }

    override suspend fun saveStation(placeId: Long, station: MgmStationInfo) {
        val first = station.candidates.firstOrNull()
        dao.upsertStation(
            MgmStationEntity(
                placeId = placeId,
                queryLatitude = station.queryLatitude,
                queryLongitude = station.queryLongitude,
                stationId = first?.id,
                name = first?.name,
                latitude = first?.latitude,
                longitude = first?.longitude,
                elevation = first?.elevation,
                resolvedAtMillis = System.currentTimeMillis(),
                candidatesJson = json.encodeToString(candidatesSerializer, station.candidates),
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
