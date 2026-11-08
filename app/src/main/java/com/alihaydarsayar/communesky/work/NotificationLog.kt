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

/** Bir özeti hangi yolun gönderdiği (ya da denediği): kanıt için; telefon ya da kullanıcı hakkında bir şey söylemez. */
enum class SummaryPath {
    /** Hedefe adım adım yaklaşan zincirin son adımı. */
    Alarm,

    /** Doğrudan hedefe kurulu yedek alarm. */
    Backup,

    /** 15 dakikada bir çalışan arka plan denetimi (WorkManager). */
    Watcher,

    /** Saatlik hava güncellemesi. */
    Hourly,

    /** Uygulama açıldı. */
    AppOpen,

    /** Telefon yeniden başladı, uygulama güncellendi ya da saat/saat dilimi değişti. */
    Restart,

    /** Yalnız hata ayıklama sürümündeki "şimdi gönder". */
    Debug,
}

/**
 * Bir deneme. [detail] yalnız istisna sınıf adı ya da bir sayıdır (kısa, güvenli karakterler); yer adı, konum ya da
 * hava verisi hiçbir zaman buraya yazılmaz. [via]: denemeyi yapan yol (1.5.12 ve önceki kayıtlarda yok).
 */
data class Attempt(
    val at: Instant, val kind: AttemptKind, val outcome: AttemptOutcome, val detail: String? = null, val via: SummaryPath? = null,
) {
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

    /** Satır: zaman,tür,sonuç[,ayrıntı[,yol]]; ayrıntı yoksa ama yol varsa ayrıntı alanı boş kalır. */
    fun encode(entries: List<Attempt>): String = entries.joinToString("\n") {
        val head = "${it.at.toEpochMilli()},${it.kind.name},${it.outcome.name}"
        when {
            it.via != null -> "$head,${it.detail.orEmpty()},${it.via.name}"
            it.detail != null -> "$head,${it.detail}"
            else -> head
        }
    }

    /** Bozuk satırlar sessizce atlanır. 1.5.12 ve öncesinin 3–4 alanlı satırları da okunur. */
    fun decode(text: String?): List<Attempt> = text.orEmpty().lines().mapNotNull { line ->
        val part = line.split(',')
        if (part.size !in 3..5) return@mapNotNull null
        val detail = part.getOrNull(3)?.takeIf { it.isNotEmpty() }
        if (detail != null && !Attempt.DETAIL.matches(detail)) return@mapNotNull null
        val at = part[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        val kind = AttemptKind.entries.firstOrNull { it.name == part[1] } ?: return@mapNotNull null
        val outcome = AttemptOutcome.entries.firstOrNull { it.name == part[2] } ?: return@mapNotNull null
        val via = part.getOrNull(4)?.let { name -> SummaryPath.entries.firstOrNull { it.name == name } ?: return@mapNotNull null }
        Attempt(at, kind, outcome, detail, via)
    }

    /**
     * Gönderilemeyen en son özet denemesi (Ayarlar › Bildirimler › Bildirim durumu'ndaki "Son kaçırılan" satırı):
     * gönderilmemiş bir sonuç (atlandı, engellendi, hata, kaçırıldı ya da yalnız "güncel hava alınamadı" bildirimi) ve
     * aynı türün aynı gün ondan sonra gönderilmiş bir özeti yok. Test denemeleri sayılmaz. Saf; test edilir.
     */
    fun lastMissed(entries: List<Attempt>, zone: ZoneId): Attempt? =
        entries.filter { it.kind != AttemptKind.Test && !it.outcome.delivered }
            .filter { failed ->
                val day = failed.at.atZone(zone).toLocalDate()
                entries.none { it.kind == failed.kind && it.outcome.delivered && !it.at.isBefore(failed.at) && it.at.atZone(zone).toLocalDate() == day }
            }
            .maxByOrNull { it.at }

    /** "Kaçırıldı" kaydında [Attempt.detail] planlanan zamandır (epoch ms); ötekilerde denemenin zamanı. */
    fun missedTime(attempt: Attempt): Instant =
        attempt.detail?.takeIf { attempt.outcome == AttemptOutcome.Missed }?.toLongOrNull()?.let(Instant::ofEpochMilli) ?: attempt.at

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

/** Hangi alarmın çaldığı: zincirin son adımı ya da doğrudan hedefe kurulu yedek. */
enum class AlarmWhich { Final, Backup }

/**
 * Bir özet alarmının çaldığı an ("alarm alıcısı tetiklendi"); bildirimin gösterilmesinden ayrı tutulur. [target]
 * alarmın kurulduğu hedef saat, [delay] gecikmedir. Yalnız zamanlar ve tür; başka bilgi yoktur.
 */
data class AlarmFire(val at: Instant, val kind: AttemptKind, val target: Instant, val which: AlarmWhich) {
    val delay: Duration get() = Duration.between(target, at)
}

/** Alarm kaydı: en çok [MAX] kayıt, 7 günden eskisi silinir. Saf; test edilir. */
object AlarmTrace {

    const val MAX = 10

    fun add(entries: List<AlarmFire>, fire: AlarmFire, now: Instant): List<AlarmFire> = prune(entries + fire, now)

    fun prune(entries: List<AlarmFire>, now: Instant): List<AlarmFire> =
        entries.filter { Duration.between(it.at, now) <= AttemptLog.MAX_AGE }.sortedBy { it.at }.takeLast(MAX)

    fun encode(entries: List<AlarmFire>): String =
        entries.joinToString("\n") { "${it.at.toEpochMilli()},${it.kind.name},${it.target.toEpochMilli()},${it.which.name}" }

    fun decode(text: String?): List<AlarmFire> = text.orEmpty().lines().mapNotNull { line ->
        val part = line.split(',')
        if (part.size != 4) return@mapNotNull null
        val at = part[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        val kind = AttemptKind.entries.firstOrNull { it.name == part[1] } ?: return@mapNotNull null
        val target = part[2].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
        val which = AlarmWhich.entries.firstOrNull { it.name == part[3] } ?: return@mapNotNull null
        AlarmFire(at, kind, target, which)
    }

    /** Bir türün en son çalan alarmı. */
    fun last(entries: List<AlarmFire>, kind: AttemptKind): AlarmFire? = entries.filter { it.kind == kind }.maxByOrNull { it.at }
}

/** [AttemptLog]'un cihazdaki yeri (uygulamanın kendi özel dosyası). */
class NotificationLog(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun entries(now: Instant = Instant.now()): List<Attempt> = AttemptLog.prune(AttemptLog.decode(prefs.getString(KEY, null)), now)

    @Synchronized
    fun record(kind: AttemptKind, outcome: AttemptOutcome, now: Instant = Instant.now(), detail: String? = null, via: SummaryPath? = null) {
        val updated = AttemptLog.add(AttemptLog.decode(prefs.getString(KEY, null)), Attempt(now, kind, outcome, detail, via), now)
        prefs.edit().putString(KEY, AttemptLog.encode(updated)).apply()
    }

    /** Son alarm çalışları (en çok 10, 7 gün). */
    fun alarms(now: Instant = Instant.now()): List<AlarmFire> = AlarmTrace.prune(AlarmTrace.decode(prefs.getString(ALARMS, null)), now)

    /** Alarm alıcısı tetiklendi (bildirim gösterilmeden önce; ayrı kayıt). */
    @Synchronized
    fun recordAlarm(kind: AttemptKind, target: Instant, which: AlarmWhich, now: Instant = Instant.now()) {
        val updated = AlarmTrace.add(AlarmTrace.decode(prefs.getString(ALARMS, null)), AlarmFire(now, kind, target, which), now)
        prefs.edit().putString(ALARMS, AlarmTrace.encode(updated)).apply()
    }

    /** Zincirin en son ara adımı: çaldığı an ve hedef (her tür için yalnız sonuncusu). */
    fun lastHop(kind: AttemptKind, now: Instant = Instant.now()): Pair<Instant, Instant>? {
        val part = prefs.getString("hop_${kind.name}", null)?.split(',')?.takeIf { it.size == 2 } ?: return null
        val at = part[0].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        val target = part[1].toLongOrNull()?.let(Instant::ofEpochMilli) ?: return null
        return (at to target).takeIf { Duration.between(at, now) <= AttemptLog.MAX_AGE }
    }

    fun recordHop(kind: AttemptKind, target: Instant, now: Instant = Instant.now()) {
        prefs.edit().putString("hop_${kind.name}", "${now.toEpochMilli()},${target.toEpochMilli()}").apply()
    }

    /** 15 dakikalık arka plan denetiminin en son çalıştığı an. */
    fun lastWatch(): Instant? = prefs.getLong(WATCH, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun recordWatch(now: Instant = Instant.now()) {
        prefs.edit().putLong(WATCH, now.toEpochMilli()).apply()
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
        const val ALARMS = "alarms"
        const val WATCH = "watch_last"
    }
}
