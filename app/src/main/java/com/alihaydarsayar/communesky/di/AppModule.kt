package com.alihaydarsayar.communesky.di

import android.content.Context
import androidx.room.Room
import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.data.local.WeatherDatabase
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt'e "şu sınıfı istersen böyle oluştur" tarifleri. Her biri uygulama boyunca tek kopya (Singleton).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // API'nin döndürdüğü ama bizim kullanmadığımız alanlar hata vermesin.
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApi(json: Json): OpenMeteoApi {
        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db")
            // Sadece önbellek: şema değişirse eski veriyi silip baştan başlamak sorun değil.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWeatherCacheDao(database: WeatherDatabase): WeatherCacheDao = database.weatherCacheDao()
}
