package com.alihaydarsayar.communesky.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alihaydarsayar.communesky.data.places.PlacesRepository
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.1 → 1.2 veritabanı geçişleri (1 → 2 → 3 → 4) ve kayıtlı yer kuralları. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WeatherDatabase::class.java,
    )

    @Test
    fun migration1To4KeepsTheCachedWeather() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO weather_cache (id, cityName, latitude, longitude, isCurrentLocation, " +
                    "forecastJson, fetchedAtMillis) VALUES (0, 'Tuzla', 40.81, 29.30, 1, '{}', 123)",
            )
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)
        db.query("SELECT cityName, isCurrentLocation, fetchedAtMillis, accuracyMeters FROM weather_cache WHERE id = 0")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Tuzla", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(123L, cursor.getLong(2))
                assertTrue(cursor.isNull(3))
            }
        db.query("SELECT COUNT(*) FROM places").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM observations").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun atMostOneHomeAndReorderAndUndo() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java).build()
        val repository = PlacesRepository(database)
        val atakum = repository.add(PlaceSearchResult("Atakum", "Samsun, Türkiye", "TR", 41.33, 36.27))
        val tuzla = repository.add(PlaceSearchResult("Tuzla", "İstanbul, Türkiye", "TR", 40.82, 29.30))
        val limerick = repository.add(PlaceSearchResult("Limerick", "Ireland", "IE", 52.66, -8.63))

        // Aynı yer ikinci kez eklenmez.
        assertEquals(atakum, repository.add(PlaceSearchResult("Atakum", null, "TR", 41.34, 36.26)))
        assertEquals(listOf(atakum, tuzla, limerick), repository.all().map { it.id })

        repository.setHome(atakum)
        repository.setHome(tuzla)
        assertEquals(listOf(tuzla), repository.all().filter { it.isHome }.map { it.id })
        repository.setHome(null)
        assertTrue(repository.all().none { it.isHome })

        repository.reorder(listOf(limerick, atakum, tuzla))
        assertEquals(listOf(limerick, atakum, tuzla), repository.all().map { it.id })

        repository.setHome(atakum)
        val removed = repository.remove(atakum)!!
        assertEquals(listOf(limerick, tuzla), repository.all().map { it.id })
        repository.restore(removed)
        assertEquals(listOf(limerick, atakum, tuzla), repository.all().map { it.id })
        assertEquals(listOf(atakum), repository.all().filter { it.isHome }.map { it.id })
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
