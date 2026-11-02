package com.alihaydarsayar.communesky.data.remote

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.time.Duration

/**
 * Sunucudan gelen bir hata: hangi servis (yalnız ana makine adı), HTTP kodu ve ne zaman. [code] 0: sunucuya hiç
 * ulaşılamadı (bağlantı yok ya da zaman aşımı). Yer, koordinat ve sorgu metni asla tutulmaz.
 */
data class NetworkFailure(val at: Long, val host: String, val code: Int, val retryAfterSeconds: Int?)

/**
 * Son birkaç ağ hatasının listesi: en çok [MAX] kayıt, [KEEP] süreden eskisi silinir. Saf; test edilir. Kayıt biçimi:
 * `zaman,host,kod[,retryAfter]`.
 */
object NetworkFailureList {
    const val MAX = 5
    val KEEP: Duration = Duration.ofDays(7)

    /** Ana makine adı yalnız harf, rakam, nokta ve tire içerir (yol, sorgu ve yer bilgisi giremez). */
    fun cleanHost(host: String): String =
        host.takeIf { it.length in 1..64 && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } } ?: "other"

    fun add(list: List<NetworkFailure>, failure: NetworkFailure, now: Long): List<NetworkFailure> =
        prune(listOf(failure) + list, now).take(MAX)

    fun prune(list: List<NetworkFailure>, now: Long): List<NetworkFailure> =
        list.filter { now - it.at <= KEEP.toMillis() && it.at <= now + 60_000 }.sortedByDescending { it.at }

    fun encode(list: List<NetworkFailure>): String =
        list.joinToString(";") { "${it.at},${it.host},${it.code}" + (it.retryAfterSeconds?.let { r -> ",$r" } ?: "") }

    fun decode(text: String?): List<NetworkFailure> = text.orEmpty().split(";").mapNotNull { part ->
        val fields = part.split(",")
        if (fields.size !in 3..4) return@mapNotNull null
        val at = fields[0].toLongOrNull() ?: return@mapNotNull null
        val code = fields[2].toIntOrNull()?.takeIf { it in 0..599 } ?: return@mapNotNull null
        NetworkFailure(at, cleanHost(fields[1]), code, fields.getOrNull(3)?.toIntOrNull()?.takeIf { it in 0..86_400 })
    }
}

/** Hata ve başarı bildirimlerinin alıcısı; [NetworkFailureInterceptor] test edilebilsin diye ayrıdır. */
interface FailureSink {
    fun record(host: String, code: Int, retryAfterSeconds: Int?)
    fun markSuccess()
}

/** Telefonda kalan kısa ağ hatası kaydı ve son başarılı ana tahmin zamanı. Hiçbir yere gönderilmez. */
class NetworkFailureLog(context: Context) : FailureSink {
    private val prefs = context.applicationContext.getSharedPreferences("network_failures", Context.MODE_PRIVATE)

    override fun record(host: String, code: Int, retryAfterSeconds: Int?) = record(host, code, retryAfterSeconds, System.currentTimeMillis())

    @Synchronized
    fun record(host: String, code: Int, retryAfterSeconds: Int?, now: Long) {
        val list = NetworkFailureList.decode(prefs.getString(KEY, null))
        val failure = NetworkFailure(now, NetworkFailureList.cleanHost(host), code, retryAfterSeconds)
        prefs.edit().putString(KEY, NetworkFailureList.encode(NetworkFailureList.add(list, failure, now))).apply()
    }

    fun entries(now: Long = System.currentTimeMillis()): List<NetworkFailure> =
        NetworkFailureList.prune(NetworkFailureList.decode(prefs.getString(KEY, null)), now)

    override fun markSuccess() {
        prefs.edit().putLong(SUCCESS, System.currentTimeMillis()).apply()
    }

    /** Ana tahminin en son başarıyla alındığı an; hiç alınmadıysa null. */
    fun lastSuccess(): Long? = prefs.getLong(SUCCESS, 0L).takeIf { it > 0 }

    private companion object {
        const val KEY = "failures"
        const val SUCCESS = "last_success"
    }
}

/** Servislerin kullanıcıya gösterilen adı; bilinmeyen ana makine adı olduğu gibi gösterilir. */
object ServiceName {
    fun of(host: String?): String? = when {
        host == null -> null
        host.endsWith("open-meteo.com") -> "Open-Meteo"
        host.endsWith("mgm.gov.tr") -> "MGM"
        host.endsWith("aviationweather.gov") -> "NOAA"
        host.endsWith("komoot.io") -> "Photon"
        else -> host
    }
}

/**
 * Hata alan isteği ne zaman ve kaç kez yeniden deneyeceği. 429 ve 5xx geçicidir. Üstel bekleme (1 sn, 2 sn) ve sunucu
 * `Retry-After` dediyse en az o kadar; ama toplam bekleme sınırlıdır: tek bir bekleme [MAX_WAIT_MS] değerini aşıyorsa
 * hiç beklenmez (arka plan işi sonra yeniden dener). Saf; test edilir.
 */
object RetryPolicy {
    const val MAX_RETRIES = 2
    const val BASE_MS = 1_000L
    const val MAX_WAIT_MS = 6_000L

    fun retryable(code: Int) = code == 429 || code in 500..599

    /** [attempt]: kaçıncı yeniden deneme (0'dan). Null: yeniden deneme yok. */
    fun delayMs(attempt: Int, code: Int, retryAfterSeconds: Int?): Long? {
        if (!retryable(code) || attempt >= MAX_RETRIES) return null
        val backoff = BASE_MS shl attempt
        val wait = maxOf(backoff, (retryAfterSeconds ?: 0) * 1000L)
        return wait.takeIf { it <= MAX_WAIT_MS }
    }
}

/**
 * Bütün servis isteklerinin başında durur: 429 ve 5xx cevaplarını [RetryPolicy]'ye göre yeniden dener, kalıcı
 * hataları (host, kod, zaman) [NetworkFailureLog]'a yazar ve ana tahminin başarısını işaretler. İstek iptal edilirse
 * bekleme hemen biter.
 */
class NetworkFailureInterceptor(
    private val log: FailureSink,
    private val delayMs: (attempt: Int, code: Int, retryAfterSeconds: Int?) -> Long? = RetryPolicy::delayMs,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (!chain.call().isCanceled()) log.record(host, 0, null)
                throw e
            }
            val code = response.code
            if (code < 400) {
                if (isForecast(request.url)) log.markSuccess()
                return response
            }
            if (!RetryPolicy.retryable(code)) return response
            val retryAfter = response.header("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
            val wait = delayMs(attempt, code, retryAfter)
            if (wait == null || chain.call().isCanceled()) {
                log.record(host, code, retryAfter)
                return response
            }
            response.close()
            if (!sleep(wait, chain)) throw IOException("Canceled")
            attempt++
        }
    }

    /** Bekler; bu sırada istek iptal edilirse false. */
    private fun sleep(millis: Long, chain: Interceptor.Chain): Boolean {
        val until = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < until) {
            if (chain.call().isCanceled()) return false
            Thread.sleep(100)
        }
        return true
    }

    private fun isForecast(url: HttpUrl) = url.host == "api.open-meteo.com" && url.encodedPath == "/v1/forecast" && url.queryParameter("models") == null
}
