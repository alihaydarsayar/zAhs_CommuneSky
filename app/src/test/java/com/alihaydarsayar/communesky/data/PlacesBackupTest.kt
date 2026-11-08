package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.places.BackedUpPlace
import com.alihaydarsayar.communesky.data.places.PlacesBackupFormat
import com.alihaydarsayar.communesky.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kayıtlı yerlerin yedek kopyası: kimlik, sıra ve Ev işareti korunur; bozuk dosya hiçbir şey getirmez. */
class PlacesBackupTest {

    private val places = listOf(
        SavedPlace(3, "Beşiktaş", "İstanbul, Türkiye", 41.04, 29.0, isHome = true, sortOrder = 0),
        SavedPlace(7, "Limerick", "Ireland", 52.66, -8.63, isHome = false, sortOrder = 1),
    )

    @Test
    fun `places come back with the same ids, order and home mark`() {
        val back = PlacesBackupFormat.decode(PlacesBackupFormat.encode(places))
        assertEquals(
            listOf(
                BackedUpPlace(3, "Beşiktaş", "İstanbul, Türkiye", 41.04, 29.0, 0, isHome = true),
                BackedUpPlace(7, "Limerick", "Ireland", 52.66, -8.63, 1, isHome = false),
            ),
            back,
        )
    }

    @Test
    fun `a broken or foreign file restores nothing`() {
        assertTrue(PlacesBackupFormat.decode(null).isEmpty())
        assertTrue(PlacesBackupFormat.decode("not json").isEmpty())
        assertTrue(PlacesBackupFormat.decode("""{"version":1,"places":[{"id":0,"name":"x","latitude":1,"longitude":1,"sortOrder":0}]}""").isEmpty())
        assertTrue(PlacesBackupFormat.decode("""{"version":1,"places":[{"id":2,"name":"x","latitude":95,"longitude":1,"sortOrder":0}]}""").isEmpty())
    }

    @Test
    fun `at most one home and no duplicate ids`() {
        val text = """{"version":1,"places":[
            {"id":1,"name":"a","latitude":1,"longitude":1,"sortOrder":0,"isHome":true},
            {"id":2,"name":"b","latitude":1,"longitude":1,"sortOrder":1,"isHome":true},
            {"id":2,"name":"c","latitude":1,"longitude":1,"sortOrder":2}]}"""
        val back = PlacesBackupFormat.decode(text)
        assertEquals(listOf(1L, 2L), back.map { it.id })
        assertEquals(listOf(true, false), back.map { it.isHome })
    }

    @Test
    fun `places are restored only once, on a fresh install with an empty database`() {
        assertTrue(PlacesBackupFormat.shouldImport(checked = false, hasPlaces = false, backupExists = true))
        // Güncelleme: yerler zaten var.
        assertFalse(PlacesBackupFormat.shouldImport(checked = false, hasPlaces = true, backupExists = true))
        // Kullanıcı sonradan bütün yerlerini sildi: geri gelmez.
        assertFalse(PlacesBackupFormat.shouldImport(checked = true, hasPlaces = false, backupExists = true))
        assertFalse(PlacesBackupFormat.shouldImport(checked = false, hasPlaces = false, backupExists = false))
    }
}
