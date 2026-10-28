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
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: ObservationDao,
    private val json: Json,
) : ObservationStore {

    private val candidatesSerializer = ListSerializer(MgmCandidate.serializer())

    override val appVersion: Int by lazy {
        runCatching { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt() }.getOrDefault(0)
    }

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
        MgmStationInfo(
            entity.queryLatitude, entity.queryLongitude, candidates,
            resolvedAtMillis = entity.resolvedAtMillis, appVersion = entity.appVersion, complete = entity.complete,
        )
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
                resolvedAtMillis = station.resolvedAtMillis,
                appVersion = station.appVersion,
                complete = station.complete,
                candidatesJson = json.encodeToString(candidatesSerializer, station.candidates),
            ),
        )
    }

    // İstasyon listesi büyük (~2.000 istasyon) ve bütün yerlerde ortak: veritabanına değil, tek bir dosyaya yazılır.
    private val directoryFile get() = java.io.File(context.filesDir, "mgm_istasyonlar.json")

    override suspend fun stationDirectory(): StationDirectory? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { json.decodeFromString(StationDirectory.serializer(), directoryFile.readText()) }.getOrNull()
    }

    override suspend fun saveStationDirectory(directory: StationDirectory) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val temporary = java.io.File(context.filesDir, "mgm_istasyonlar.json.tmp")
        temporary.writeText(json.encodeToString(StationDirectory.serializer(), directory))
        if (!temporary.renameTo(directoryFile)) {
            directoryFile.delete()
            temporary.renameTo(directoryFile)
        }
        Unit
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
