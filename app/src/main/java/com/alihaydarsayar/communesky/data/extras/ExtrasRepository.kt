package com.alihaydarsayar.communesky.data.extras

import com.alihaydarsayar.communesky.model.CoordinatePrivacy
import com.alihaydarsayar.communesky.data.Place
import com.alihaydarsayar.communesky.data.mgm.MgmAlertDto
import com.alihaydarsayar.communesky.data.mgm.MgmForecastApi
import com.alihaydarsayar.communesky.data.mgm.MgmParsing
import com.alihaydarsayar.communesky.data.observation.ObservationRepository
import com.alihaydarsayar.communesky.data.remote.OpenMeteoModels
import com.alihaydarsayar.communesky.data.remote.OpenMeteoModelsApi
import com.alihaydarsayar.communesky.model.CandidateSeries
import com.alihaydarsayar.communesky.model.ForecastSource
import com.alihaydarsayar.communesky.model.GeoDistance
import com.alihaydarsayar.communesky.model.PlaceExtras
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ana tahmine eklenen veriler: Türkiye'de MGM'nin tahmini, uyarıları ve mevsim normali; her yerde öteki
 * modellerin (ECMWF…) tahminleri. Hiçbir zaman hata fırlatmaz: bir kaynak cevap vermezse o parça eski
 * hâliyle kalır ya da boş geçilir, ekran ana tahminle çalışmaya devam eder.
 *
 * Tazelik eşikleri kaynağın kendi güncellenme sıklığına göredir; değişmemiş veri boşuna yeniden istenmez.
 * Kullanıcı aşağı çekip yenilerse ([force]) eşiklere bakılmaz. Bütün istekler aynı anda gider.
 */
@Singleton
class ExtrasRepository @Inject constructor(
    private val mgm: Lazy<MgmForecastApi>,
    private val modelsApi: Lazy<OpenMeteoModelsApi>,
    private val airApi: Lazy<com.alihaydarsayar.communesky.data.remote.AirQualityApi>,
    private val store: ExtrasStore,
) {
    private val mutex = Mutex()

    /** Verilen yerlerin ek verilerini yeniler; bir şey değiştiyse true. */
    suspend fun refresh(places: List<Place>, force: Boolean = false, now: Instant = Instant.now()): Boolean = mutex.withLock {
        if (places.isEmpty()) return@withLock false
        withContext(Dispatchers.IO) {
            coroutineScope {
                // Uyarı listeleri bütün Türkiye içindir: kaç yer olursa olsun bir kez indirilir, o da gerekirse.
                val alerts = async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    val today = async { attempt("uyarılar (bugün)") { mgm.get().alertsToday() } }
                    val tomorrow = async { attempt("uyarılar (yarın)") { mgm.get().alertsTomorrow() } }
                    val lists = listOfNotNull(today.await(), tomorrow.await())
                    if (lists.isEmpty()) null else lists.flatten()
                }
                val changed = places.map { place -> async { refreshOne(place, force, now, alerts) } }.awaitAll().any { it }
                alerts.cancel()
                changed
            }
        }
    }

    suspend fun delete(placeId: Long) = store.delete(placeId)

    private suspend fun refreshOne(place: Place, force: Boolean, now: Instant, alerts: Deferred<List<MgmAlertDto>?>): Boolean = coroutineScope {
        val lat = place.city.latitude
        val lon = place.city.longitude
        val saved = store.get(place.id)
        // Yer değiştiyse (ör. "Bulunduğum yer") eski yerin merkezi, tahmini ve kararları geçersizdir.
        var extras = saved?.takeIf { GeoDistance.kilometers(it.queryLatitude, it.queryLongitude, lat, lon) < MOVED_KM }
            ?: PlaceExtras(queryLatitude = lat, queryLongitude = lon)
        val millis = now.toEpochMilli()

        val models = async {
            if (!ExtrasFreshness.isStale(extras.modelsFetchedAt, millis, ExtrasFreshness.FORECAST, force)) return@async null
            val sources = ExtrasFreshness.modelsFor(extras)
            attempt("modeller") {
                OpenMeteoModels.parse(
                    modelsApi.get().models(CoordinatePrivacy.round(lat), CoordinatePrivacy.round(lon), OpenMeteoModels.query(sources)),
                    sources,
                )
            }?.takeIf { it.isNotEmpty() }
        }

        // Hava kalitesi: saatte bir, öteki isteklerle aynı anda.
        val air = async {
            if (!ExtrasFreshness.isStale(extras.airFetchedAt, millis, ExtrasFreshness.AIR, force)) return@async null
            attempt("hava kalitesi") { airApi.get().airQuality(CoordinatePrivacy.round(lat), CoordinatePrivacy.round(lon)).toModel() }
                ?.takeIf { quality -> quality.hours.any { it.aqi != null } }
        }

        if (ObservationRepository.isInTurkey(lat, lon)) {
            if (extras.centerId == null) {
                attempt("MGM merkez") { mgm.get().center(CoordinatePrivacy.round(lat), CoordinatePrivacy.round(lon)) }?.let { center ->
                    val province = center.il?.let { il -> attempt("MGM il merkezi") { mgm.get().provinceCenters(il) } }
                    extras = extras.copy(
                        centerId = center.merkezId,
                        hourlyStationId = center.saatlikTahminIstNo,
                        dailyStationId = center.gunlukTahminIstNo,
                        provinceCenterId = province?.firstOrNull()?.merkezId ?: center.merkezId,
                    )
                }
            }
            val centerId = extras.centerId
            if (centerId != null) {
                val staleForecast = ExtrasFreshness.isStale(extras.nationalFetchedAt, millis, ExtrasFreshness.FORECAST, force)
                val hourly = async { extras.hourlyStationId?.takeIf { staleForecast }?.let { id -> attempt("MGM saatlik") { MgmParsing.hours(mgm.get().hourly(id)) } } }
                val daily = async { extras.dailyStationId?.takeIf { staleForecast }?.let { id -> attempt("MGM günlük") { MgmParsing.days(mgm.get().daily(id)) } } }
                val today = LocalDate.now(TURKEY)
                val staleNormal = extras.normal?.let { it.day != today.dayOfMonth || it.month != today.monthValue } ?: ExtrasFreshness.isStale(extras.normalFetchedAt, millis, ExtrasFreshness.RETRY, force)
                val normal = async { extras.provinceCenterId?.takeIf { staleNormal }?.let { id -> attempt("MGM normal") { MgmParsing.normal(mgm.get().normals(id, today.monthValue, today.dayOfMonth)) } } }
                val staleAlerts = ExtrasFreshness.isStale(extras.alertsFetchedAt, millis, ExtrasFreshness.ALERTS, force)
                val placeAlerts = if (staleAlerts) alerts.await()?.let { MgmParsing.alerts(it, centerId) } else null

                if (staleForecast) {
                    val hours = hourly.await().orEmpty()
                    val days = daily.await().orEmpty()
                    // İkisi de gelmediyse eski tahmin durur ama "ulaşılamadı" diye işaretlenir; sonraki istekte yeniden denenir.
                    extras = if (hours.isEmpty() && days.isEmpty()) {
                        extras.copy(nationalFailed = true)
                    } else {
                        extras.copy(national = CandidateSeries(ForecastSource.Mgm, hours, days), nationalFetchedAt = millis, nationalFailed = false)
                    }
                }
                if (staleNormal) extras = extras.copy(normal = normal.await() ?: extras.normal?.takeIf { !staleNormal }, normalFetchedAt = millis)
                if (placeAlerts != null) extras = extras.copy(alerts = placeAlerts, alertsFetchedAt = millis)
            }
        }
        models.await()?.let { extras = extras.copy(models = it, modelsFetchedAt = millis) }
        air.await()?.let { extras = extras.copy(airQuality = it, airFetchedAt = millis) }

        if (extras == saved) return@coroutineScope false
        store.save(place.id, extras)
        true
    }

    /** Hata, boş cevap ya da 5 saniyelik zaman aşımında null. */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        withTimeoutOrNull(TIMEOUT_MS) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val MOVED_KM = 2.0
        val TURKEY: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}

/** Tazelik eşikleri ve hangi modellerin isteneceği. Saf mantık; test edilir. */
object ExtrasFreshness {
    /** Saatlik ve günlük tahmin: 30 dakikadan eskiyse yeniden istenir. */
    const val FORECAST = 30 * 60_000L

    /** Uyarılar: 15 dakika. */
    const val ALERTS = 15 * 60_000L

    /** Hava kalitesi: saatte bir (model saatlik güncellenir). */
    const val AIR = 55 * 60_000L

    /** Gelmeyen bir verinin yeniden denenmesi: bir saat. */
    const val RETRY = 60 * 60_000L

    fun isStale(fetchedAtMillis: Long, nowMillis: Long, thresholdMillis: Long, force: Boolean): Boolean =
        force || fetchedAtMillis <= 0 || nowMillis - fetchedAtMillis >= thresholdMillis || nowMillis < fetchedAtMillis

    /**
     * Bu yer için Open-Meteo'dan istenecek modeller: hakemin karşılaştıracağı adaylar. Fransa'da Météo-France,
     * Birleşik Krallık ve İrlanda'da Met Office modeli de istenir (Open-Meteo'nun kendi seçimine dahil değildir).
     */
    fun modelsFor(extras: PlaceExtras): List<ForecastSource> =
        listOfNotNull(extras.region.localModel, ForecastSource.EcmwfIfs, ForecastSource.EcmwfAifs, ForecastSource.OpenMeteo)
}
