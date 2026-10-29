package com.alihaydarsayar.communesky.work

import android.content.Context
import java.time.Duration
import java.time.Instant

/** Hangi bildirimin denemesi. */
enum class AttemptKind { Morning, Evening, Test }

/** Bir bildirim denemesinin sonucu. Serbest metin yoktur: yalnız bu sabit değerler saklanır. */
enum class AttemptOutcome {
    /** Gönderildi ve bildirim listesinde görüldü. */
    Sent,

    /** Eski veriden kurulup gönderildi (bildirimde verinin saati yazar). */
    SentOldData,

    /** Atlandı ya da gösterilemedi: bildirim izni kapalı. */
    NoPermission,

    /** Bildirimler uygulama için Android'de kapalı (izin verilmiş olsa bile). */
    AppOff,

    /** İlgili bildirim kanalı Android'de kapalı. */
    ChannelOff,

    /** Henüz hava verisi yok. */
    NoData,

    /** Veri 24 saatten eski ve yenilenemedi: yanlış bilgi vermektense gönderilmedi. */
    DataTooOld,

    /** Android bildirimi kabul etti ama etkin bildirimler listesinde görünmedi. */
    NotVisible,

    /** Android bildirimi reddetti (istisna). */
    Failed,
    ;

    /** Bildirim gerçekten gönderildi (gün için "gönderildi" sayılır). */
    val delivered: Boolean get() = this == Sent || this == SentOldData || this == NotVisible
}

data class Attempt(val at: Instant, val kind: AttemptKind, val outcome: AttemptOutcome)

/**
 * Son bildirim denemelerinin kısa kaydı: en çok [MAX] kayıt, [MAX_AGE]'den eskisi silinir. Yalnız zaman, tür ve sonuç
 * tutulur; konum, yer adı ya da hava verisi yoktur ve hiçbir yere gönderilmez. Saf kısım test edilir.
 */
object AttemptLog {

    const val MAX = 5
    val MAX_AGE: Duration = Duration.ofDays(7)

    /**
     * [attempt]'i ekler. Aynı türün bir önceki kaydıyla aynı sonuçsa (saatlik yedek yol aynı engele tekrar çarptı)
     * yeni kayıt açmaz, onun zamanını günceller; böylece tekrarlar listeyi doldurmaz.
     */
    fun add(entries: List<Attempt>, attempt: Attempt, now: Instant): List<Attempt> {
        val kept = prune(entries, now)
        val last = kept.lastOrNull { it.kind == attempt.kind }
        val merged = if (last != null && last.outcome == attempt.outcome) kept - last else kept
        return (merged + attempt).sortedBy { it.at }.takeLast(MAX)
    }

    fun prune(entries: List<Attempt>, now: Instant): List<Attempt> =
        entries.filter { Duration.between(it.at, now) <= MAX_AGE }.sortedBy { it.at }.takeLast(MAX)

    fun encode(entries: List<Attempt>): String = entries.joinToString("\n") { "${it.at.toEpochMilli()},${it.kind.name},${it.outcome.name}" }

    /** Bozuk satırlar sessizce atlanır. */
    fun decode(text: String?): List<Attempt> = text.orEmpty().lines().mapNotNull { line ->
        val part = line.split(',')
        if (part.size != 3) return@mapNotNull null
        val at = part[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        val kind = AttemptKind.entries.firstOrNull { it.name == part[1] } ?: return@mapNotNull null
        val outcome = AttemptOutcome.entries.firstOrNull { it.name == part[2] } ?: return@mapNotNull null
        Attempt(at, kind, outcome)
    }

    /** En son başarıyla gönderilen özet (test hariç). */
    fun lastSent(entries: List<Attempt>): Attempt? = entries.filter { it.kind != AttemptKind.Test && it.outcome.delivered }.maxByOrNull { it.at }

    /** Bir türün en son denemesi. */
    fun last(entries: List<Attempt>, kind: AttemptKind): Attempt? = entries.filter { it.kind == kind }.maxByOrNull { it.at }
}

/** [AttemptLog]'un cihazdaki yeri (uygulamanın kendi özel dosyası). */
class NotificationLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun entries(now: Instant = Instant.now()): List<Attempt> = AttemptLog.prune(AttemptLog.decode(prefs.getString(KEY, null)), now)

    @Synchronized
    fun record(kind: AttemptKind, outcome: AttemptOutcome, now: Instant = Instant.now()) {
        val updated = AttemptLog.add(AttemptLog.decode(prefs.getString(KEY, null)), Attempt(now, kind, outcome), now)
        prefs.edit().putString(KEY, AttemptLog.encode(updated)).apply()
    }

    private companion object {
        const val PREFS = "notification_log"
        const val KEY = "entries"
    }
}
