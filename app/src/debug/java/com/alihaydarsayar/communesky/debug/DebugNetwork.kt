package com.alihaydarsayar.communesky.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.UnknownHostException

/**
 * Yalnız debug sürümünde: ağ hatalarını denemek için sahte arıza. Release derlemesinde bu dosya yoktur (boş karşılığı
 * src/release altında). Adb ile açılır:
 *
 *   adb shell am broadcast -a com.alihaydarsayar.communesky.debug.FAULT -n com.alihaydarsayar.communesky/.debug.NetworkFaultReceiver \
 *       --es mode offline|slow|cut|bad|error|none [--es host mgm|open-meteo|aviationweather|photon|all] [--ei ms 12000]
 */
object DebugNetwork {
    const val DEMO_OFFLINE_FLAG = "/data/local/tmp/communesky_demo_offline"

    enum class Mode { None, Offline, Slow, Cut, Bad, Error }

    @Volatile var mode: Mode = Mode.None
    @Volatile var host: String = ""
    @Volatile var slowMillis: Long = 12_000

    private fun matches(url: String) = host.isEmpty() || host == "all" || url.contains(host, ignoreCase = true)

    fun install(builder: OkHttpClient.Builder) {
        builder.addInterceptor { chain ->
            val request = chain.request()
            // Örnek veri görselleri için (bkz. DemoWeather): bu dosya varken internet hiç yokmuş gibi davranılır; uygulama
            // yeniden başlasa da sürer (mode yalnız bellekte durur). Dosyayı `adb shell rm` ile silmek eski haline getirir.
            val demo = java.io.File(DEMO_OFFLINE_FLAG)
            if (demo.exists()) {
                // Tahmin istekleri aynı örnek veriyle yanıtlanır (yenileme "başarılı" olur, ekranda hata notu çıkmaz); diğerleri başarısız olur.
                DemoWeather.respond(request.url, demo.readText())?.let { body ->
                    return@addInterceptor Response.Builder()
                        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                throw UnknownHostException("debug: örnek veri, internet yok (${request.url.host})")
            }
            if (mode == Mode.None || !matches(request.url.toString())) return@addInterceptor chain.proceed(request)
            when (mode) {
                Mode.Offline -> throw UnknownHostException("debug: internet yok (${request.url.host})")
                Mode.Slow -> {
                    // İstek süresi zaman aşımını (20 sn) aşarsa gerçek bir zaman aşımı gibi biter.
                    val until = System.currentTimeMillis() + slowMillis
                    while (System.currentTimeMillis() < until) {
                        if (chain.call().isCanceled()) throw IOException("Canceled")
                        Thread.sleep(200)
                    }
                    chain.proceed(request)
                }
                Mode.Cut -> {
                    // Gövdenin yalnız ilk 300 baytı gelir: yarıda kesilen yanıt.
                    val response = chain.proceed(request)
                    val type = response.body?.contentType()
                    val all = response.body?.bytes() ?: ByteArray(0)
                    response.newBuilder().body(all.copyOfRange(0, minOf(300, all.size)).toResponseBody(type)).build()
                }
                Mode.Bad -> Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("<html>bozuk cevap</html>".toResponseBody("application/json".toMediaType()))
                    .build()
                Mode.Error -> Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1).code(503).message("Service Unavailable")
                    .body("".toResponseBody(null))
                    .build()
                Mode.None -> chain.proceed(request)
            }
        }
    }
}

/** Adb'den gelen sahte arıza isteği (yalnız debug). */
class NetworkFaultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getStringExtra("mode")?.lowercase()
        DebugNetwork.mode = when (mode) {
            "offline" -> DebugNetwork.Mode.Offline
            "slow" -> DebugNetwork.Mode.Slow
            "cut" -> DebugNetwork.Mode.Cut
            "bad" -> DebugNetwork.Mode.Bad
            "error" -> DebugNetwork.Mode.Error
            else -> DebugNetwork.Mode.None
        }
        DebugNetwork.host = intent.getStringExtra("host").orEmpty()
        if (intent.hasExtra("ms")) DebugNetwork.slowMillis = intent.getIntExtra("ms", 12_000).toLong()
        android.util.Log.i("DebugNetwork", "arıza=${DebugNetwork.mode} host='${DebugNetwork.host}'")
    }
}
