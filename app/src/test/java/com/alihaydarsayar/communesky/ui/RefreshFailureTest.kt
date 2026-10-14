package com.alihaydarsayar.communesky.ui

import com.alihaydarsayar.communesky.ui.home.LoadError
import com.alihaydarsayar.communesky.ui.home.RefreshFailure
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/** Yenileme hataları: kesilen istek sessiz kalır, gerçek ağ ve sunucu hataları gösterilir. */
class RefreshFailureTest {

    @Test
    fun `a refresh cut short by a newer one is not an error`() {
        assertNull(RefreshFailure.of(CancellationException("konum değişti")))
        // OkHttp iptal edilen isteği böyle bitirir.
        assertNull(RefreshFailure.of(IOException("Canceled")))
        assertNull(RefreshFailure.of(IOException("istek yarıda kaldı", IOException("Canceled"))))
    }

    @Test
    fun `real network and server errors are still shown`() {
        assertEquals(LoadError.Network, RefreshFailure.of(UnknownHostException("api.open-meteo.com")))
        assertEquals(LoadError.Network, RefreshFailure.of(IOException("timeout")))
        assertEquals(LoadError.Server(503), RefreshFailure.of(HttpException(Response.error<Any>(503, "".toResponseBody()))))
        assertEquals(LoadError.Unknown, RefreshFailure.of(IllegalStateException("bozuk cevap")))
    }
}
