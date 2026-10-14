package com.alihaydarsayar.communesky.data.mgm

import com.alihaydarsayar.communesky.model.AlertEvent
import com.alihaydarsayar.communesky.model.AlertLevel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * MGM cevaplarının okunması. Örnekler servisin gerçek biçimine göre elle yazıldı (örnek ilçe: Beşiktaş);
 * gerçek bir yerin verisi değildir.
 */
class MgmParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `daily dates end with Z but are local time in Türkiye`() {
        // Günlük tahminin tarihlerinde sondaki Z yanlış: değer Türkiye saatidir.
        assertEquals(LocalDateTime.of(2026, 10, 12, 15, 0), MgmParsing.localTime("2026-10-12T15:00:00.000Z"))
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0), MgmParsing.localTime("2026-10-12T00:00:00.000Z"))
        assertNull(MgmParsing.localTime(null))
        assertNull(MgmParsing.localTime("yarın"))
    }

    @Test
    fun `hourly forecast stamps are UTC and become Türkiye time`() {
        // Saatlik tahminde Z doğru: 12:00 UTC, Türkiye'de 15:00; 21:00 UTC ertesi günün 00:00'ı.
        assertEquals(LocalDateTime.of(2026, 10, 14, 15, 0), MgmParsing.utcTime("2026-10-14T12:00:00.000Z"))
        assertEquals(LocalDateTime.of(2026, 10, 15, 0, 0), MgmParsing.utcTime("2026-10-14T21:00:00.000Z"))
        assertNull(MgmParsing.utcTime(null))
        assertNull(MgmParsing.utcTime("2026-10-14T12:00"))
    }

    @Test
    fun `hourly forecast keeps the steps the service gives and drops missing values`() {
        val response = json.decodeFromString(
            ListSerializer(MgmHourlyForecastDto.serializer()),
            """[{"baslangicZamani":"2026-10-12T12:00:00.000Z","istNo":17060,"merkez":"BESIKTAS","tahmin":[
              {"tarih":"2026-10-12T15:00:00.000Z","hadise":"PB","sicaklik":21,"nem":77,"ruzgarYonu":102,"ruzgarHizi":4,"maksimumRuzgarHizi":8},
              {"tarih":"2026-10-12T18:00:00.000Z","hadise":"GSY","sicaklik":-9999,"nem":-9999,"ruzgarYonu":167,"ruzgarHizi":5,"maksimumRuzgarHizi":12},
              {"tarih":"2026-10-12T21:00:00.000Z","hadise":"XYZ","sicaklik":16,"nem":85,"ruzgarYonu":172,"ruzgarHizi":3,"maksimumRuzgarHizi":9}]}]""",
        )
        val hours = MgmParsing.hours(response)
        assertEquals(listOf("2026-10-12T18:00", "2026-10-12T21:00", "2026-10-13T00:00"), hours.map { it.time })
        assertEquals(21.0, hours[0].temperature!!, 0.0)
        assertEquals(2, hours[0].weatherCode)
        assertEquals(8.0, hours[0].gust!!, 0.0)
        // -9999: veri yok.
        assertNull(hours[1].temperature)
        assertNull(hours[1].humidity)
        assertEquals(95, hours[1].weatherCode)
        // Tanımadığımız olay: kod yok, diğer değerler durur.
        assertNull(hours[2].weatherCode)
        assertEquals(16.0, hours[2].temperature!!, 0.0)
        // İstasyonu olmayan (kırsal) yer: boş cevap.
        assertTrue(MgmParsing.hours(emptyList()).isEmpty())
    }

    @Test
    fun `daily forecast takes the night low from the field of the next day`() {
        val response = json.decodeFromString(
            ListSerializer(JsonObject.serializer()),
            """[{"istNo":93414,
              "tarihGun0":"2026-10-11T00:00:00.000Z","hadiseGun0":"PB","enYuksekGun0":26,"enDusukGun0":15,"ruzgarHizGun0":7,"ruzgarYonGun0":204,
              "tarihGun1":"2026-10-12T00:00:00.000Z","hadiseGun1":"GSY","enYuksekGun1":27,"enDusukGun1":17,"ruzgarHizGun1":10,"ruzgarYonGun1":142,
              "tarihGun2":"2026-10-13T00:00:00.000Z","hadiseGun2":"Y","enYuksekGun2":23,"enDusukGun2":15,"ruzgarHizGun2":8,"ruzgarYonGun2":193,
              "tarihGun3":"2026-10-14T00:00:00.000Z","hadiseGun3":"KGY","enYuksekGun3":21,"enDusukGun3":-9999,"ruzgarHizGun3":27,"ruzgarYonGun3":19,
              "tarihGun4":"2026-10-15T00:00:00.000Z","hadiseGun4":"A","enYuksekGun4":18,"enDusukGun4":14,"ruzgarHizGun4":29,"ruzgarYonGun4":356,
              "tarihGun5":"2026-10-16T00:00:00.000Z","hadiseGun5":"AB","enYuksekGun5":19,"enDusukGun5":13,"ruzgarHizGun5":16,"ruzgarYonGun5":39}]""",
        )
        val days = MgmParsing.days(response)
        assertEquals(6, days.size)
        assertEquals("2026-10-12", days[1].date)
        assertEquals(27.0, days[1].maxTemperature!!, 0.0)
        // 12 Ekim gecesinin en düşüğü, 13 Ekim alanındadır (15°), kendi alanında (17°) değil.
        assertEquals(15.0, days[1].minTemperature!!, 0.0)
        assertEquals(95, days[1].weatherCode)
        // 13 Ekim gecesi: 14 Ekim alanı -9999, yani veri yok.
        assertNull(days[2].minTemperature)
        // Son günün gecesi serviste yok.
        assertNull(days[5].minTemperature)
        assertEquals(19.0, days[5].maxTemperature!!, 0.0)
    }

    private val alerts = json.decodeFromString(
        ListSerializer(MgmAlertDto.serializer()),
        """[{"_id":"a1","alertNo":2026101203,
             "text":{"yellow":"Kuvvetli sağanak bekleniyor.","orange":"Çok kuvvetli sağanak bekleniyor."},
             "weather":{"yellow":["thunderstorm"],"orange":["rain","wind"],"red":[]},
             "towns":{"yellow":[93414,93401],"orange":[93420],"red":[]},
             "begin":"2026-10-12T09:00:00.934Z","end":"2026-10-12T20:59:00.934Z"},
            {"_id":"a2","alertNo":2026101204,
             "text":{"red":"Fırtına bekleniyor."},
             "weather":{"yellow":[],"orange":[],"red":["wind"]},
             "towns":{"yellow":[],"orange":[],"red":[93420]},
             "begin":"2026-10-13T03:00:00.000Z","end":"2026-10-13T12:00:00.000Z"}]""",
    )

    @Test
    fun `a warning matches the district code under its color`() {
        val yellow = MgmParsing.alerts(alerts, centerId = 93414).single()
        assertEquals(AlertLevel.Yellow, yellow.level)
        assertEquals(listOf(AlertEvent.Thunderstorm), yellow.events)
        assertEquals("Kuvvetli sağanak bekleniyor.", yellow.text)
        // Uyarı saatleri UTC: 09:00Z Türkiye'de 12:00, 20:59Z gün sonu 23:59 (MGM'nin kendi sayfası da böyle gösterir).
        assertEquals("2026-10-12T12:00", yellow.begin)
        assertEquals("2026-10-12T23:59", yellow.end)
        // Başka bir ilçe: turuncu ve kırmızı; en ağır olan önce.
        val other = MgmParsing.alerts(alerts, centerId = 93420)
        assertEquals(listOf(AlertLevel.Red, AlertLevel.Orange), other.map { it.level })
        assertEquals(listOf(AlertEvent.Rain, AlertEvent.Wind), other[1].events)
    }

    @Test
    fun `warning times are UTC like on the MGM warning page`() {
        // MGM sitesi bu uyarıyı "14 Ekim 2026 20.00 - 15 Ekim 2026 09.00" diye gösterir (UTC okuyup +03:00 ile yazar).
        val alert = json.decodeFromString(
            ListSerializer(MgmAlertDto.serializer()),
            """[{"_id":"a3","alertNo":2026101439,"text":{"yellow":"Sağanak."},"weather":{"yellow":["thunderstorm"],"orange":[],"red":[]},
              "towns":{"yellow":[93414],"orange":[],"red":[]},"begin":"2026-10-14T17:00:27.223Z","end":"2026-10-15T06:00:27.223Z"}]""",
        )
        val warning = MgmParsing.alerts(alert, centerId = 93414).single()
        assertEquals("2026-10-14T20:00", warning.begin)
        assertEquals("2026-10-15T09:00", warning.end)
    }

    @Test
    fun `a place that is not in any list has no warning`() {
        assertTrue(MgmParsing.alerts(alerts, centerId = 91201).isEmpty())
        // Bugün ve yarın listelerinde aynı uyarı varsa bir kez sayılır.
        assertEquals(1, MgmParsing.alerts(alerts + alerts, centerId = 93414).size)
    }

    @Test
    fun `the seasonal normal is the long-term average for the day`() {
        val response = json.decodeFromString(
            ListSerializer(MgmNormalDto.serializer()),
            """[{"ay":10,"gun":12,"istNo":93401,"max":29.2,"maxOrt":21.6,"merkezId":93401,"min":10.4,"minOrt":14.6,"ort":17.5}]""",
        )
        val normal = MgmParsing.normal(response)!!
        assertEquals(21.6, normal.maxAverage, 0.0)
        assertEquals(14.6, normal.minAverage!!, 0.0)
        // 4° üstünde; 2°'den küçük fark "normallerinde" (0).
        assertEquals(4, normal.difference(25.6))
        assertEquals(0, normal.difference(23.0))
        assertEquals(-3, normal.difference(18.4))
        assertNull(MgmParsing.normal(emptyList()))
    }
}
