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

    /** Özet bundan eski bir önbellekten kurulursa bildirimde önbelleğin saati belirtilir. */
    val FRESH: Duration = Duration.ofHours(3)

    /** Önbellek bundan eskiyse özet gönderilmez (yanlış bilgi vermektense susar). */
    val MAX_CACHE_AGE: Duration = Duration.ofHours(24)

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
     * Yedek yol şimdi göndermeli mi: bugünün saati geçti, bugün gönderilmedi ve pencere ([CATCH_UP], en çok gün
     * sonuna kadar) içindeyiz.
     */
    fun shouldCatchUp(now: ZonedDateTime, minutes: Int, sentOn: LocalDate?): Boolean {
        val today = now.toLocalDate()
        if (sentOn == today) return false
        val target = target(today, minutes, now.zone)
        val end = minOf(target.plus(CATCH_UP), today.plusDays(1).atStartOfDay(now.zone))
        return !now.isBefore(target) && now.isBefore(end)
    }

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
}
