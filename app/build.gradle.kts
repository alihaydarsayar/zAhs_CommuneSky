plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.alihaydarsayar.communesky"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.alihaydarsayar.communesky"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: kullanılmayan kodu atar, kalanı küçültüp hızlandırır.
            optimization {
                enable = true
            }
            // Uygulama kişisel kullanım için; release sürümü de debug anahtarıyla imzalanır,
            // böylece Android Studio'dan doğrudan kurulabilir.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Birim testlerinde android.util.Log gibi çağrılar hata vermesin, sessizce geçilsin.
        unitTests.isReturnDefaultValues = true
    }
    androidResources {
        // values-xx klasörlerinden dil listesini üretir; Android 13+ "Uygulama dilleri" ayarında görünür.
        generateLocaleConfig = true
    }
    bundle {
        language {
            // Dil, Ayarlar'dan uygulamanın içinde değiştirilebiliyor: bütün diller paketle birlikte kurulur.
            // Yoksa mağaza yalnızca telefonun dillerini kurar ve seçilen dilin metinleri eksik kalır.
            enableSplit = false
        }
    }
    lint {
        // Bilerek kapatılan denetimler:
        // - RtlHardcoded: sağdan sola yazılan dil desteklenmiyor; widget düzenleri (saat kadranı, ibreler) mutlak konumludur.
        // - UnusedAttribute: yalnızca yeni Android sürümlerinde okunan öznitelikler (widget boyut sınırları vb.); eskilerde yok sayılır.
        // - InconsistentLayout: layout-v31'deki ek öğeler yalnızca Android 12+ özellikleri içindir.
        // - VectorRaster, VectorPath: kadran ve ibre çizimleri bilerek büyük ve ayrıntılı vektörlerdir.
        // - Typos: Türkçe ikilemeler ("tek tek", "hemen hemen", "yapış yapış") yazım hatası değildir.
        // - UseKtx: yalnızca yazım önerisi.
        // - Sürüm önerileri (kütüphane, Kotlin, AGP): güncellemeler ayrı bir iş olarak yapılır.
        disable += setOf(
            "RtlHardcoded", "UnusedAttribute", "InconsistentLayout", "VectorRaster", "VectorPath", "Typos", "UseKtx",
            "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion",
        )
    }
}

composeCompiler {
    // java.time ve model sınıflarımızın değişmez olduğunu Compose'a söyler; gereksiz yeniden çizimleri önler.
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

room {
    // Veritabanı şemasının geçmişi; ileride tablo değişirse geçiş (migration) yazmak için gerekir.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
