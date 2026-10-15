package com.alihaydarsayar.communesky.debug

import com.alihaydarsayar.communesky.model.AlertLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Release sürümünde sahte uyarı yoktur; debug sürümündeki deneme aracının boş karşılığı. */
object FakeAlerts {
    val levels: Flow<Map<Long, AlertLevel>> = flowOf(emptyMap())

    fun current(): Map<Long, AlertLevel> = emptyMap()
}
