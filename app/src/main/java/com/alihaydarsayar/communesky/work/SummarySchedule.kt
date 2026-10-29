package com.alihaydarsayar.communesky.work

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Sabah ve akşam özetinin zamanlaması. Saf hesap; test edilir.
 *
 * Bir özetin saati o günün seçilen dakikasıdır ([target]). Özet günde bir kez gönderilir: o gün gönderildiyse ya
 * da saat geçtiyse bir sonraki alarm yarınındır. Alarm hiç çalmazsa (süreç öldürüldü, alarm silindi) saatlik iş
 * yedek olarak saatten sonra [CATCH_UP] içinde, aynı gün bitmeden gönderir; pencere geçtiyse o günün özeti atlanır.
 */
object SummarySchedule {

    /** Seçilen saatten sonra yedek yolun hâlâ göndereceği süre. */
    val CATCH_UP: Duration = Duration.ofHours(3)

    /**
     * Yedek yol seçilen saatten bu kadar sonra devreye girer: alarm (en çok birkaç dakika sapar) önce çalışsın, önbelleği
     * yenileyip özeti kendisi göndersin; yedek yalnız alarm gelmediyse göndersin.
     */
    val CATCH_UP_GRACE: Duration = Duration.ofMinutes(15)

    /** Özet bundan eski bir önbellekten kurulursa bildirimde önbelleğin saati belirtilir. */
    val FRESH: Duration = Duration.ofHours(3)

    /** Önbellek bundan eskiyse özet gönderilmez (yanlış bilgi vermektense susar). */
    val MAX_CACHE_AGE: Duration = Duration.ofHours(24)

    /**
     * Alarm yayınının (goAsync) toplam süresi: Android ~10 sn sonra alıcıyı ANR sayar. Yükleme, kısa ağ yenilemesi ve
     * bildirimin listede görünmesini beklemek bu sürenin içinde kalır.
     */
    const val RECEIVER_BUDGET_MS = 9_000L
    const val LOAD_TIMEOUT_MS = 4_000L
    const val MAX_REFRESH_MS = 3_500L

    /** Bildirimin listede görünmesini beklemek için ayrılan süre ([NotificationHealth.awaitShown]: 10 × 150 ms). */
    const val VERIFY_MS = 1_500L

    /** Alarm geldiğinden beri [elapsedMs] geçti: ağ yenilemesine kalan süre (0: yenilemeyi atla, eldeki veriyle gönder). */
    fun refreshBudgetMs(elapsedMs: Long): Long = (RECEIVER_BUDGET_MS - elapsedMs - VERIFY_MS).coerceIn(0L, MAX_REFRESH_MS)

    /**
     * Özetin "şimdi"si. Tahmin yerin saatindedir ama özetin günü telefonun gününe göre seçilir: özet telefonun saatinde
     * gelir ve kullanıcı o günü bekler (telefon UTC+4, yer UTC+3 olsa da). Gün tarihi farklıysa (gece yarısı civarı)
     * bütün gün anlatılır.
     */
    fun summaryNow(placeNow: java.time.LocalDateTime, deviceDate: LocalDate): java.time.LocalDateTime =
        if (placeNow.toLocalDate() == deviceDate) placeNow else deviceDate.atStartOfDay()

    /** [date] gününün seçilen saati ([minutes]: gece yarısından itibaren dakika), [zone] saatine göre. */
    fun target(date: LocalDate, minutes: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(minutes / 60, minutes % 60), zone)

    /**
     * Bir sonraki alarmın zamanı: bugünün saati henüz gelmediyse ve bugün gönderilmediyse bugünün, yoksa yarının.
     * [sentOn]: son gönderilen günün tarihi (hiç gönderilmediyse null).
     */
    fun nextTrigger(now: ZonedDateTime, minutes: Int, sentOn: LocalDate?): ZonedDateTime {
        val today = target(now.toLocalDate(), minutes, now.zone)
        return if (sentOn != now.toLocalDate() && now.isBefore(today)) today else target(now.toLocalDate().plusDays(1), minutes, now.zone)
    }

    /**
     * Yedek yol şimdi göndermeli mi: bugünün saatinden [CATCH_UP_GRACE] geçti, bugün gönderilmedi ve pencere
     * ([CATCH_UP] sonuna kadar, en çok gün sonuna) içindeyiz.
     */
    fun shouldCatchUp(now: ZonedDateTime, minutes: Int, sentOn: LocalDate?): Boolean {
        val today = now.toLocalDate()
        if (sentOn == today) return false
        val target = target(today, minutes, now.zone)
        return !now.isBefore(target.plus(CATCH_UP_GRACE)) && now.isBefore(catchUpEnd(target))
    }

    /** Yedek pencerenin sonu: hedeften [CATCH_UP] sonra, en çok o günün sonu. */
    fun catchUpEnd(target: ZonedDateTime): ZonedDateTime =
        minOf(target.plus(CATCH_UP), target.toLocalDate().plusDays(1).atStartOfDay(target.zone))

    /**
     * Kullanıcı özeti açtı ya da saatini değiştirdi ve seçtiği saat bugün çoktan geçti mi? O zaman bugünün özeti
     * atlanır (ilk özet yarın gelir): ayar ekranından çıkınca kimse beklemediği bir bildirim almasın.
     */
    fun passedToday(now: ZonedDateTime, minutes: Int): Boolean = !now.isBefore(target(now.toLocalDate(), minutes, now.zone))

    /** Önbelleğin yaşına göre özetin ne olacağı. */
    enum class Verdict { Send, SendCached, Skip }

    /**
     * Gönderilsin mi? [force] (yalnız hata ayıklama): açık/gönderilmiş denetimlerini atlar. [cacheAge] null ise
     * önbellek yoktur.
     */
    fun verdict(enabled: Boolean, sentOn: LocalDate?, today: LocalDate, cacheAge: Duration?, force: Boolean = false): Verdict = when {
        !enabled && !force -> Verdict.Skip
        !force && sentOn == today -> Verdict.Skip
        cacheAge == null || cacheAge > MAX_CACHE_AGE -> Verdict.Skip
        cacheAge > FRESH -> Verdict.SendCached
        else -> Verdict.Send
    }

    /**
     * [verdict] göndermiyorsa kayda yazılacak neden. Özet kapalıysa ya da bugün zaten gönderildiyse null: söylenecek
     * bir şey yok, saatlik yedek yolun her turu kayıt doldurmasın. [force] için [verdict] ile aynı kurallar.
     */
    fun skipOutcome(enabled: Boolean, sentOn: LocalDate?, today: LocalDate, cacheAge: Duration?, force: Boolean = false): AttemptOutcome? = when {
        !enabled && !force -> null
        !force && sentOn == today -> null
        cacheAge == null -> AttemptOutcome.NoData
        cacheAge > MAX_CACHE_AGE -> AttemptOutcome.DataTooOld
        else -> null
    }

    /** Bir alarm ya da yedek turunda yapılacak iş. */
    enum class Action { Full, FullOldData, StaleNotice, NoDataNotice, Nothing }

    /**
     * Önbelleğin yaşına göre ne yapılacağı. [verdict] ile aynı kurallar; fark: veri 24 saatten eskiyse (ya da hiç yoksa)
     * özet sessizce atlanmaz, günde en çok bir kez "güncel hava alınamadı" bildirimi gönderilir ([noticeOn]: bildirimin
     * son gönderildiği gün). Özet kapalıysa ya da bugün gönderildiyse yapılacak bir şey yoktur.
     */
    fun action(enabled: Boolean, sentOn: LocalDate?, noticeOn: LocalDate?, today: LocalDate, cacheAge: Duration?, force: Boolean = false): Action =
        when (verdict(enabled, sentOn, today, cacheAge, force)) {
            Verdict.Send -> Action.Full
            Verdict.SendCached -> Action.FullOldData
            Verdict.Skip -> when {
                !enabled && !force -> Action.Nothing
                !force && sentOn == today -> Action.Nothing
                noticeOn == today && !force -> Action.Nothing
                cacheAge == null -> Action.NoDataNotice
                else -> Action.StaleNotice
            }
        }
}
