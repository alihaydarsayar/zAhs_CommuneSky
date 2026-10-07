package com.alihaydarsayar.communesky.ui.sky

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.withFrameNanos

/**
 * Animasyonların saati: her ekran karesinde (60/90/120 Hz) saniye cinsinden ilerler.
 *
 * Performans/pil için:
 * - Uygulama arka plandayken durur (repeatOnLifecycle), geri gelince kaldığı yerden devam eder.
 * - Telefonda "Animasyonları kaldır" açıksa hiç çalışmaz; sahne sabit görünür.
 * - Değeri sadece çizim aşamasında okunur; her karede yeniden "compose" yapılmaz, sadece yeniden çizilir.
 */
@Composable
fun rememberSceneTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    // Uzun bir takılmadan sonra animasyon zıplamasın diye kare süresini sınırla.
                    val delta = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    // Float hassasiyeti uzun sürelerde azalmasın diye saati bir saatte bir başa sar.
                    time.floatValue = (time.floatValue + delta) % 3600f
                }
            }
        }
    }
    return time
}
