package com.alihaydarsayar.communesky.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.model.AirHour
import com.alihaydarsayar.communesky.model.AirQuality
import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import com.alihaydarsayar.communesky.model.WeatherAlert
import com.alihaydarsayar.communesky.widget.WidgetDataLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Elle deneme yardımcısı: uyarı ve hava kalitesi bildirimlerini, gerçek bir uyarı ya da kötü hava beklemeden
 * bir kez tetikler. Bildirimi gönderen kod uygulamanınkidir; yalnızca veri uydurulur (ilk kayıtlı yere (yoksa bulunduğum yere) turuncu
 * bir uyarı ya da kötü hava kalitesi eklenir). Ayarlar'daki anahtar kapalıysa ya da bildirim izni yoksa hiçbir
 * şey gönderilmez: "kapalıyken gelmemeli" de böyle denenir.
 */
@RunWith(AndroidJUnit4::class)
class NotificationRunTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test
    fun alert() = runBlocking {
        val input = WidgetDataLoader(context).input.first()
        val snapshot = (input.weather.values.firstOrNull { !it.isCurrentLocation } ?: input.weather.values.first())
        val now = snapshot.forecast.localNow().withSecond(0).withNano(0)
        val alert = WeatherAlert(
            id = "deneme-${System.currentTimeMillis()}",
            level = AlertLevel.Orange,
            events = listOf(AlertEvent.Thunderstorm, AlertEvent.Rain),
            text = "Kuvvetli gök gürültülü sağanak yağış bekleniyor.",
            begin = now.toString(),
            end = now.plusHours(6).toString(),
            source = "MGM",
        )
        AlertNotifier.check(context, listOf(snapshot.copy(alerts = listOf(alert))), input.settings.notifications, input.home?.snapshot?.placeId)
    }

    @Test
    fun airQuality() = runBlocking {
        val input = WidgetDataLoader(context).input.first()
        val snapshot = (input.weather.values.firstOrNull { !it.isCurrentLocation } ?: input.weather.values.first())
        val hour = snapshot.forecast.localNow().withMinute(0).withSecond(0).withNano(0)
        // Bugün gönderildiyse yeniden gönderebilmek için kayıt silinir.
        context.getSharedPreferences("air_quality_notifications", Context.MODE_PRIVATE).edit().clear().apply()
        val poor = AirQuality(hours = listOf(AirHour(time = hour.toString(), aqi = 85, pm25 = 60.0)))
        AirQualityNotifier.check(context, listOf(snapshot.copy(airQuality = poor)), input.settings.notifications)
    }
}
