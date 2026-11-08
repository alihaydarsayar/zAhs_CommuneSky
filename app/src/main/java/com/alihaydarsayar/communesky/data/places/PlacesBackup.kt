package com.alihaydarsayar.communesky.data.places

import android.content.Context
import androidx.room.withTransaction
import com.alihaydarsayar.communesky.data.local.PlaceEntity
import com.alihaydarsayar.communesky.data.local.WeatherDatabase
import com.alihaydarsayar.communesky.model.SavedPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Yedekteki bir yer: yalnız kullanıcının kaydettikleri (ad, açıklama, koordinat, sıra, Ev işareti). */
@Serializable
data class BackedUpPlace(
    val id: Long,
    val name: String,
    val region: String? = null,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
    val isHome: Boolean = false,
)

@Serializable
private data class PlacesFile(val version: Int = 1, val places: List<BackedUpPlace> = emptyList())

/**
 * Android'in otomatik yedeği için kayıtlı yerlerin küçük bir kopyası. Veritabanı (hava önbelleği, istasyonlar,
 * doğruluk kayıtları) yedeğe girmez (bkz. res/xml/data_extraction_rules.xml); yerler bu dosyadan geri konur. Saf
 * kısım test edilir.
 */
object PlacesBackupFormat {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(places: List<SavedPlace>): String = json.encodeToString(
        PlacesFile.serializer(),
        PlacesFile(places = places.map { BackedUpPlace(it.id, it.name, it.region, it.latitude, it.longitude, it.sortOrder, it.isHome) }),
    )

    /** Bozuk ya da tanınmayan dosya boş liste verir; aynı kimlik iki kez varsa ilki alınır, en çok bir Ev kalır. */
    fun decode(text: String?): List<BackedUpPlace> {
        val places = runCatching { json.decodeFromString(PlacesFile.serializer(), text.orEmpty()).places }.getOrDefault(emptyList())
            .filter { it.name.isNotBlank() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.id > 0 }
            .distinctBy { it.id }
        val home = places.firstOrNull { it.isHome }?.id
        return places.map { it.copy(isHome = it.id == home) }
    }

    /**
     * Yedekten yerler geri konmalı mı: bu kurulumda henüz bakılmadı ([checked] yedeklenmeyen klasördeki işaret),
     * veritabanında hiç yer yok ve yedek dosyası var. Böylece kullanıcı sonradan bütün yerlerini silerse geri gelmez.
     */
    fun shouldImport(checked: Boolean, hasPlaces: Boolean, backupExists: Boolean): Boolean = !checked && !hasPlaces && backupExists
}

@Singleton
class PlacesBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: WeatherDatabase,
) {
    private val file get() = File(File(context.filesDir, DIRECTORY), FILE)

    /** Yedeklenmeyen klasörde (noBackupFilesDir): bu kurulumda yedeğe bakıldı mı. */
    private val checkedMarker get() = File(context.noBackupFilesDir, CHECKED)

    /**
     * Uygulama açılırken bir kez: yeni bir kurulumda (yedekten geri yüklenmiş olabilir) veritabanı boşsa yerleri
     * dosyadan geri koyar, kimlikleri aynen korur (seçili yer ve widget ayarları onlara bağlıdır). Geri konan yer
     * sayısını döndürür.
     */
    suspend fun restoreIfNeeded(): Int = withContext(Dispatchers.IO) {
        val marker = checkedMarker
        val dao = database.placeDao()
        val decision = PlacesBackupFormat.shouldImport(marker.exists(), dao.getAll().isNotEmpty(), file.exists())
        var restored = 0
        if (decision) {
            val places = PlacesBackupFormat.decode(runCatching { file.readText() }.getOrNull())
            database.withTransaction {
                for (place in places) {
                    dao.insert(PlaceEntity(place.id, place.name, place.region, place.latitude, place.longitude, place.sortOrder, isHome = false))
                }
                places.firstOrNull { it.isHome }?.let { dao.setHome(it.id) }
            }
            restored = places.size
        }
        runCatching { marker.parentFile?.mkdirs(); marker.createNewFile() }
        restored
    }

    /** Yerler değişince yedek kopya güncellenir (yarım yazılmış dosya kalmasın diye önce geçici dosyaya). */
    suspend fun save(places: List<SavedPlace>) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "$FILE.tmp")
            temporary.writeText(PlacesBackupFormat.encode(places))
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
        }
    }

    private companion object {
        const val DIRECTORY = "backup"
        const val FILE = "places.json"
        const val CHECKED = "places_backup_checked"
    }
}
