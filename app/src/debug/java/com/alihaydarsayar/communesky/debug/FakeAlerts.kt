package com.alihaydarsayar.communesky.debug

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alihaydarsayar.communesky.data.WeatherRepository
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.ui.theme.TextPrimary
import com.alihaydarsayar.communesky.work.AlertNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Yalnız debug sürümünde: seçili yere geçici, sahte bir MGM uyarısı. Uyarı şeridi, uygulama içi öneri ve
 * bildirim gerçek uyarıdaki gibi çalışır. Bellekte durur; uygulama kapanınca kendiliğinden kalkar.
 */
object FakeAlerts {
    private val state = MutableStateFlow<Map<Long, AlertLevel>>(emptyMap())

    /** Yer kimliği → sahte uyarının rengi. */
    val levels: Flow<Map<Long, AlertLevel>> = state

    fun current(): Map<Long, AlertLevel> = state.value

    /** [level] null ise sahte uyarı kaldırılır. Turuncudan kırmızıya geçiş aynı uyarının yükselmesi sayılır. */
    suspend fun set(context: Context, level: AlertLevel?) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, FakeAlertsEntryPoint::class.java)
        val placeId = entry.settingsRepository().selectedPlaceId.first()
            ?: entry.weatherRepository().allWeather.first().keys.firstOrNull()
            ?: return
        state.value = if (level == null) state.value - placeId else state.value + (placeId to level)
        // Bildirim, arka plan güncellemesini beklemeden hemen.
        val settings = entry.settingsRepository().settings.first()
        AlertNotifier.check(
            context.applicationContext,
            entry.weatherRepository().allWeather.first().values,
            settings.notifications,
            entry.placesRepository().all().firstOrNull { it.isHome }?.id,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FakeAlertsEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun settingsRepository(): SettingsRepository
    fun placesRepository(): PlacesRepository
}

/** Debug kartındaki satırlar: sahte turuncu, sahte kırmızı ve kaldır. */
@Composable
internal fun FakeAlertRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    listOf("Sahte turuncu uyarı" to AlertLevel.Orange, "Sahte kırmızı uyarı" to AlertLevel.Red, "Sahte uyarıyı kaldır" to null)
        .forEach { (title, level) ->
            Text(
                title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { scope.launch { FakeAlerts.set(context, level) } }
                    .padding(vertical = 10.dp),
            )
        }
}
