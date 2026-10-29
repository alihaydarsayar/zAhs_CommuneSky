package com.alihaydarsayar.communesky.work

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    /** Özet kurulurken hata oldu; yerine basit bir özet gönderildi (en düşük/en yüksek sıcaklık, genel durum). */
    Fallback,

    /** Veri 24 saatten eski ve yenilenemedi: özet yerine "güncel hava alınamadı" bildirimi gönderildi. */
    StaleNotice,

    /** Beklenmeyen hata; [Attempt.detail] istisnanın sınıf adıdır. */
    Error,

    /** Planlanan zaman geçti ve hiçbir deneme kaydı yok: alarm tetiklenmedi. */
    Missed,
    ;

    /** Bir özet (ya da yedeği) gerçekten gönderildi: gün için "gönderildi" sayılır. */
    val delivered: Boolean get() = this == Sent || this == SentOldData || this == NotVisible || this == Fallback
}

/**
 * Bir deneme. [detail] yalnız istisna sınıf adı ya da bir sayıdır (kısa, güvenli karakterler); yer adı, konum ya da
 * hava verisi hiçbir zaman buraya yazılmaz.
 */
data class Attempt(val at: Instant, val kind: AttemptKind, val outcome: AttemptOutcome, val detail: String? = null) {
    init {
        require(detail == null || DETAIL.matches(detail)) { "detail" }
    }

    companion object {
        val DETAIL = Regex("[A-Za-z0-9_.$]{1,64}")

        /** Bir istisnanın kayda yazılacak adı (yalnız sınıf adı; iletisi kişisel bilgi taşıyabilir, yazılmaz). */
        fun nameOf(error: Throwable): String = error.javaClass.name.takeLast(64).filter { it.isLetterOrDigit() || it == '_' || it == '.' || it == '$' }
            .ifEmpty { "Unknown" }
    }
}

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

    fun encode(entries: List<Attempt>): String =
        entries.joinToString("\n") { "${it.at.toEpochMilli()},${it.kind.name},${it.outcome.name}" + (it.detail?.let { d -> ",$d" } ?: "") }

    /** Bozuk satırlar sessizce atlanır. */
    fun decode(text: String?): List<Attempt> = text.orEmpty().lines().mapNotNull { line ->
        val part = line.split(',')
        if (part.size !in 3..4) return@mapNotNull null
        val detail = part.getOrNull(3)
        if (detail != null && !Attempt.DETAIL.matches(detail)) return@mapNotNull null
        val at = part[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        val kind = AttemptKind.entries.firstOrNull { it.name == part[1] } ?: return@mapNotNull null
        val outcome = AttemptOutcome.entries.firstOrNull { it.name == part[2] } ?: return@mapNotNull null
        Attempt(at, kind, outcome, detail)
    }

    /** Kullanıcının henüz kapatmadığı en son "kaçırıldı" kaydı ([dismissedAt]: kartın en son kapatıldığı an). */
    fun pendingMissed(entries: List<Attempt>, dismissedAt: Instant?): Attempt? =
        entries.filter { it.outcome == AttemptOutcome.Missed && (dismissedAt == null || it.at.isAfter(dismissedAt)) }.maxByOrNull { it.at }

    /** En son başarıyla gönderilen özet (test hariç). */
    fun lastSent(entries: List<Attempt>): Attempt? = entries.filter { it.kind != AttemptKind.Test && it.outcome.delivered }.maxByOrNull { it.at }

    /** Bir türün en son denemesi. */
    fun last(entries: List<Attempt>, kind: AttemptKind): Attempt? = entries.filter { it.kind == kind }.maxByOrNull { it.at }
}

/**
 * Kaçırma algılama: alarm için bir zaman planlandı ([planned]) ama yedek pencere de geçtiği halde o türden hiçbir
 * deneme kaydı (gönderildi, atlandı, hata) yok ve gün "gönderildi" diye de işaretlenmemiş: alarm tetiklenmedi.
 * Saf; test edilir.
 */
object MissedCheck {

    fun missed(planned: Instant?, kind: AttemptKind, attempts: List<Attempt>, sentOn: LocalDate?, now: Instant, zone: ZoneId): Boolean {
        if (planned == null) return false
        if (now.isBefore(SummarySchedule.catchUpEnd(planned.atZone(zone)).toInstant())) return false
        if (attempts.any { it.kind == kind && !it.at.isBefore(planned) }) return false
        return sentOn == null || sentOn.isBefore(planned.atZone(zone).toLocalDate())
    }
}

/** [AttemptLog]'un cihazdaki yeri (uygulamanın kendi özel dosyası). */
class NotificationLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun entries(now: Instant = Instant.now()): List<Attempt> = AttemptLog.prune(AttemptLog.decode(prefs.getString(KEY, null)), now)

    @Synchronized
    fun record(kind: AttemptKind, outcome: AttemptOutcome, now: Instant = Instant.now(), detail: String? = null) {
        val updated = AttemptLog.add(AttemptLog.decode(prefs.getString(KEY, null)), Attempt(now, kind, outcome, detail), now)
        prefs.edit().putString(KEY, AttemptLog.encode(updated)).apply()
    }

    /** Henüz kapatılmamış "kaçırıldı" kaydı (uygulama açılınca kart olarak gösterilir). */
    fun pendingMissed(now: Instant = Instant.now()): Attempt? =
        AttemptLog.pendingMissed(entries(now), prefs.getLong("missed_dismissed", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli))

    /** Kullanıcı "kaçırıldı" kartını kapattı: o ana kadarki kayıtlar bir daha gösterilmez. */
    fun dismissMissed(now: Instant = Instant.now()) {
        prefs.edit().putLong("missed_dismissed", now.toEpochMilli()).apply()
    }

    /** Bir özet türü için en son planlanan zaman (alarmın kurulduğu hedef); yoksa ya da 7 günden eskiyse null. */
    fun planned(kind: AttemptKind, now: Instant = Instant.now()): Instant? =
        prefs.getLong("planned_${kind.name}", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            ?.takeIf { java.time.Duration.between(it, now) <= AttemptLog.MAX_AGE }

    /** Alarm bu hedef için kuruldu ([at] null: alarm kaldırıldı, özet kapalı). Her tür için yalnız en sonuncusu tutulur. */
    @Synchronized
    fun setPlanned(kind: AttemptKind, at: Instant?) {
        prefs.edit().apply { if (at == null) remove("planned_${kind.name}") else putLong("planned_${kind.name}", at.toEpochMilli()) }.apply()
    }

    private companion object {
        const val PREFS = "notification_log"
        const val KEY = "entries"
    }
}
