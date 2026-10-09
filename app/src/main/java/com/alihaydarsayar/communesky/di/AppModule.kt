package com.alihaydarsayar.communesky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.alihaydarsayar.communesky.data.local.WeatherCacheDao
import com.alihaydarsayar.communesky.data.local.ObservationDao
import com.alihaydarsayar.communesky.data.observation.AviationWeatherApi
import com.alihaydarsayar.communesky.data.observation.MgmApi
import com.alihaydarsayar.communesky.data.local.WeatherDatabase
import com.alihaydarsayar.communesky.data.remote.OpenMeteoApi
import com.alihaydarsayar.communesky.data.search.OpenMeteoGeocodingApi
import com.alihaydarsayar.communesky.data.search.PhotonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Ekran kapansa da yarıda kalmaması gereken işler için uygulama ömrü boyunca yaşayan kapsam. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

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

    /** Bütün servisler aynı bağlantı havuzunu paylaşır. Servisler uygulamayı adıyla tanır. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "CommuneSky (Android; +https://github.com/alihaydarsayar/zAhs_CommuneSky)")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, json: Json): OpenMeteoApi =
        retrofit(OpenMeteoApi.BASE_URL, client, json).create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun providePhotonApi(client: OkHttpClient, json: Json): PhotonApi =
        retrofit(PhotonApi.BASE_URL, client, json).create(PhotonApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(client: OkHttpClient, json: Json): OpenMeteoGeocodingApi =
        retrofit(OpenMeteoGeocodingApi.BASE_URL, client, json).create(OpenMeteoGeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideMgmApi(client: OkHttpClient, json: Json): MgmApi =
        retrofit(MgmApi.BASE_URL, client, json).create(MgmApi::class.java)

    @Provides
    @Singleton
    fun provideAviationWeatherApi(client: OkHttpClient, json: Json): AviationWeatherApi =
        retrofit(AviationWeatherApi.BASE_URL, client, json).create(AviationWeatherApi::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db")
            // Artık kayıtlı yerler de burada: şema değişince veri silinmez, geçiş (migration) yapılır.
            // Sadece eski bir sürüme dönülürse (geliştirme sırasında) baştan başlanır.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideObservationDao(database: WeatherDatabase): ObservationDao = database.observationDao()

    @Provides
    fun provideWeatherCacheDao(database: WeatherDatabase): WeatherCacheDao = database.weatherCacheDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
