package com.alihaydarsayar.communesky.debug

import okhttp3.OkHttpClient

/** Release sürümünde sahte ağ arızası yoktur; debug sürümündeki aracın boş karşılığı. */
object DebugNetwork {
    @Suppress("UNUSED_PARAMETER")
    fun install(builder: OkHttpClient.Builder) = Unit
}
