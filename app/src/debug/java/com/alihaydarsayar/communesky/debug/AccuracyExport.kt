package com.alihaydarsayar.communesky.debug

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alihaydarsayar.communesky.R
import com.alihaydarsayar.communesky.data.accuracy.AccuracyDao
import com.alihaydarsayar.communesky.data.accuracy.ForecastRecordEntity
import com.alihaydarsayar.communesky.data.accuracy.ObservedRecordEntity
import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.ui.common.GlassCard
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.ui.theme.TextSecondary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Yalnız debug sürümünde derlenir. Hakemin telefonda tuttuğu tahmin ve ölçüm kayıtlarını CSV olarak paylaş
 * menüsüne verir; tools/karne bu dosyaları okur. Yer adı yazılmaz: yer kimliği ve yaklaşık koordinat yazılır.
 */
object AccuracyCsv {
    const val HEADER = "type,place_id,lat,lon,source,kind,lead_hours,target_hour_utc,value,station_km"

    private val HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC)

    /** Koordinat 0,1 dereceye (yaklaşık 10 km) yuvarlanır: bölgeyi ve en yakın havalimanını bulmaya yeter. */
    fun coordinate(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f", it) }.orEmpty()

    fun hour(epochHour: Long): String = HOUR.format(Instant.ofEpochSecond(epochHour * 3600))

    private fun number(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    /** [places]: yer kimliği → (enlem, boylam). */
    fun build(places: Map<Long, Pair<Double, Double>>, forecasts: List<ForecastRecordEntity>, observed: List<ObservedRecordEntity>): String =
        buildString {
            appendLine(HEADER)
            fun position(placeId: Long) = "${coordinate(places[placeId]?.first)},${coordinate(places[placeId]?.second)}"
            forecasts.sortedWith(compareBy({ it.placeId }, { it.targetHour }, { it.source }, { it.kind }, { it.leadHours })).forEach {
                appendLine("F,${it.placeId},${position(it.placeId)},${it.source},${it.kind},${it.leadHours},${hour(it.targetHour)},${number(it.value)},")
            }
            observed.sortedWith(compareBy({ it.placeId }, { it.hour })).forEach {
                appendLine("O,${it.placeId},${position(it.placeId)},,,,${hour(it.hour)},${number(it.temperature)},${number(it.stationKm)}")
            }
        }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugEntryPoint {
    fun accuracyDao(): AccuracyDao
    fun weatherCacheDao(): WeatherCacheDao
}

private suspend fun writeExport(context: Context): Pair<File, Int> = withContext(Dispatchers.IO) {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, DebugEntryPoint::class.java)
    val forecasts = mutableListOf<ForecastRecordEntity>()
    val observed = mutableListOf<ObservedRecordEntity>()
    val places = mutableMapOf<Long, Pair<Double, Double>>()
    // Kayıtlar yer başına tutulur; yerlerin kimliği ve koordinatı hava önbelleğinde.
    for (cache in entry.weatherCacheDao().observeAll().first()) {
        places[cache.id] = cache.latitude to cache.longitude
        forecasts += entry.accuracyDao().forecasts(cache.id, 0)
        observed += entry.accuracyDao().observed(cache.id, 0)
    }
    val folder = File(context.cacheDir, "export").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "communesky-dogruluk-${LocalDate.now()}.csv")
    file.writeText(AccuracyCsv.build(places, forecasts, observed))
    file to forecasts.size + observed.size
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugexport", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Ayarlar'ın en altındaki, yalnız debug sürümünde görünen bölüm. */
fun LazyListScope.debugSettingsItems() {
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("Hakemin tahmin ve ölçüm kayıtları, CSV (yalnız debug sürümü)") }
        GlassCard(Modifier.fillMaxWidth(), title = "Debug") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        scope.launch {
                            runCatching { writeExport(context) }
                                .onSuccess { (file, count) ->
                                    status = "$count kayıt"
                                    if (count > 0) share(context, file) else status = "Henüz kayıt yok"
                                }
                                .onFailure { status = "Dışa aktarılamadı: ${it.message}" }
                        }
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Doğruluk kayıtlarını dışa aktar", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(status, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
            // Uyarı şeridini, öneriyi ve uyarı bildirimini gerçek bir uyarı beklemeden denemek için.
            FakeAlertRows()
            // Özet bildirimlerini saatini beklemeden denemek için (ayar kapalı olsa da gönderilir).
            com.alihaydarsayar.communesky.model.today.SummaryKind.entries.forEach { kind ->
                Text(
                    "${kind.name} özetini şimdi gönder",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { scope.launch { com.alihaydarsayar.communesky.work.MorningSummary.onAlarm(context, kind, force = true) } }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
