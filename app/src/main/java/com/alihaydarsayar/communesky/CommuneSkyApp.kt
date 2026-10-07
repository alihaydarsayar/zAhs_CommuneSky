package com.alihaydarsayar.communesky

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Uygulamanın başlangıç noktası. @HiltAndroidApp, Hilt'in bağımlılık kabını burada kurar.
 * WorkManager'a da işçileri (Worker) Hilt ile oluşturmasını söylüyoruz.
 */
@HiltAndroidApp
class CommuneSkyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
