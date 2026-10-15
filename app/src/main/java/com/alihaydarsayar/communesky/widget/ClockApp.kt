package com.alihaydarsayar.communesky.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock

/**
 * Saate dokununca açılacak saat/alarm uygulaması. Önce Android'in standart "alarmları göster" isteği denenir;
 * bazı telefonların saat uygulaması bu isteği karşılamadığı için sonra bilinen saat uygulamaları, en sonda da
 * adı ya da paket adı saat gibi görünen başlatılabilir uygulama aranır. Hiçbiri yoksa null (çağıran Commune Sky'ı açar).
 */
object ClockApp {

    /** Bilinen saat uygulamalarının paket adları (Google, Samsung, Oppo/Realme/OnePlus, Xiaomi, Huawei, Honor, vivo ve diğerleri). */
    private val KNOWN = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.coloros.alarmclock",
        "com.oplus.alarmclock",
        "com.oneplus.deskclock",
        "com.huawei.deskclock",
        "com.hihonor.deskclock",
        "com.android.BBKClock",
        "com.vivo.alarmclock",
        "com.asus.deskclock",
        "com.motorola.blur.alarmclock",
        "com.lge.clock",
        "com.sonyericsson.organizer",
        "com.transsion.deskclock",
        "com.nothing.deskclock",
    )

    private val LABEL_WORDS = listOf("clock", "saat", "alarm", "horloge", "réveil", "reveil", "uhr", "wecker")

    @Volatile private var cached: Intent? = null
    @Volatile private var cachedFor: String? = null

    /** Saf mantık; test edilir: bu uygulama bir saat uygulaması gibi mi görünüyor? */
    fun looksLikeClock(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        val text = label.lowercase()
        if (pkg == "com.alihaydarsayar.communesky") return false
        if (pkg in KNOWN) return true
        // "Saat" kelimesini ("Saat" ve "Alarm" gibi kısa adları) bütün ad olarak ararız: "Saatlik hava" gibi adlar saat uygulaması değildir.
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return pkg.endsWith("clock") || pkg.contains("deskclock") || pkg.contains("alarmclock") ||
            (words.size <= 2 && words.any { it in LABEL_WORDS })
    }

    /** Saat uygulamasını açan intent; telefonda saat uygulaması bulunamazsa null. */
    fun intent(context: Context): Intent? {
        val key = context.packageName
        cached?.takeIf { cachedFor == key }?.let { return Intent(it) }
        val found = find(context.packageManager)
        if (found != null) {
            cached = found
            cachedFor = key
        }
        return found?.let { Intent(it) }
    }

    private fun find(pm: PackageManager): Intent? {
        val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (alarms.resolveActivity(pm) != null) return alarms
        for (pkg in KNOWN) {
            pm.getLaunchIntentForPackage(pkg)?.let { return it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }.getOrDefault(emptyList())
        val match = launchers.firstOrNull { looksLikeClock(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        return match?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}
