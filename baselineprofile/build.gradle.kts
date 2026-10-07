plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/**
 * Baseline Profile üretici ve açılış ölçümü.
 * Android Studio'da "Generate Baseline Profile" çalıştırılınca uygulamayı bir cihazda açar,
 * en sık kullanılan kodu kaydeder; bu kod kurulumda önceden derlenir ve uygulama daha hızlı açılır.
 */
android {
    namespace = "com.alihaydarsayar.communesky.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Bağlı bir telefon veya açık bir emülatör kullan.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
