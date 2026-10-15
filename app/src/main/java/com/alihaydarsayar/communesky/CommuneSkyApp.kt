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

    /** Sistem bellek isterse widget resimlerinin önbelleği bırakılır (gerektiğinde yeniden çizilir). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level != TRIM_MEMORY_UI_HIDDEN) com.alihaydarsayar.communesky.widget.WidgetBitmaps.clearCache()
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Debug sürümünde ana iş parçacığındaki disk ve ağ erişimleri loglanır (adb logcat -s StrictMode).
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
            )
        }
        // 1.5.2: bildirimler varsayılan kapalı. Güncelleyen ve izni olan kullanıcının uyarı ayarı korunur (bir kez).
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val info = packageManager.getPackageInfo(packageName, 0)
            settingsRepository.migrateNotifications(
                permitted = com.alihaydarsayar.communesky.work.AlertNotifier.canNotify(this@CommuneSkyApp),
                freshInstall = info.firstInstallTime == info.lastUpdateTime,
                // Eski tek kanallar silinir; yerlerine her tür için sesli ve sessiz kanal kurulur.
                blocked = com.alihaydarsayar.communesky.work.NotificationChannels.removeLegacy(this@CommuneSkyApp),
            )
            com.alihaydarsayar.communesky.work.NotificationChannels.ensure(this@CommuneSkyApp)
        }
        // Saat biçimi tercihi ilk ekrandan önce arka planda okunur; ana iş parçacığı diski beklemesin.
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.alihaydarsayar.communesky.widget.WidgetLanguage.warmUp(this@CommuneSkyApp) }
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
