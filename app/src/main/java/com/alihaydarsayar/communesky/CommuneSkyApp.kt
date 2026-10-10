package com.alihaydarsayar.communesky

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.data.settings.SettingsRepository
import com.alihaydarsayar.communesky.di.ApplicationScope
import com.alihaydarsayar.communesky.widget.WeatherWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Uygulamanın başlangıç noktası. @HiltAndroidApp, Hilt'in bağımlılık kabını burada kurar.
 * WorkManager'a da işçileri (Worker) Hilt ile oluşturmasını söylüyoruz.
 */
@HiltAndroidApp
class CommuneSkyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var placesRepository: PlacesRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Birim, Ev ya da kayıtlı yerler değişince widget'lar hemen yeni hale geçsin
        // (hava verisi değişmediği için internete çıkılmaz). İlk değer atlanır: açılışta gereksiz çizim yok.
        appScope.launch {
            combine(placesRepository.places, settingsRepository.settings) { places, settings -> places to settings }
                .drop(1)
                .debounce(500)
                .collect { WeatherWidgetUpdater.updateAll(this@CommuneSkyApp) }
        }
        // Duvar kâğıdı değişince cam ve saydam widget'ların yazı rengi yeniden seçilir. Sadece
        // sistemin hesapladığı renk ipuçları dinlenir; duvar kâğıdının kendisi okunmaz, izin gerekmez.
        // Dinleyici uygulama süreci yaşarken çalışır; süreç kapalıysa renk bir sonraki veri
        // güncellemesinde düzelir (bunun için ayrıca uyanılmaz).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            runCatching {
                android.app.WallpaperManager.getInstance(this).addOnColorsChangedListener(
                    { _, which ->
                        if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0) {
                            appScope.launch { WeatherWidgetUpdater.updateAll(this@CommuneSkyApp) }
                        }
                    },
                    android.os.Handler(android.os.Looper.getMainLooper()),
                )
            }
        }
        // Android 15+: widget seçicideki önizlemeler gerçek widget çizimiyle (sürüm başına bir kez).
        appScope.launch { runCatching { WeatherWidgetUpdater.publishPreviews(this@CommuneSkyApp) } }
    }
}
