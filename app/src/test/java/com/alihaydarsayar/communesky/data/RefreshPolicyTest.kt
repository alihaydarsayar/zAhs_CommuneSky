package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.extras.SideCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** Gereksiz tekrar isteğin önlenmesi ve yan servis hatasının ana tahmini bozmaması. */
class RefreshPolicyTest {

    private val now = 10_000_000L

    @Test
    fun `a forecast fetched a minute ago for the same place is not asked again`() {
        assertFalse(ForecastFreshness.needsRefresh(now - 60_000, 41.04, 29.0, 41.04, 29.0, now))
    }

    @Test
    fun `an older forecast, a missing one or a place that moved is asked again`() {
        assertTrue(ForecastFreshness.needsRefresh(now - 5 * 60_000, 41.04, 29.0, 41.04, 29.0, now))
        assertTrue(ForecastFreshness.needsRefresh(null, null, null, 41.04, 29.0, now))
        assertTrue(ForecastFreshness.needsRefresh(now - 60_000, 41.04, 29.0, 53.4, -2.99, now))
        // Saat geriye alındıysa önbelleğe güvenilmez.
        assertTrue(ForecastFreshness.needsRefresh(now + 60_000, 41.04, 29.0, 41.04, 29.0, now))
    }

    @Test
    fun `a side service answering 503, 429 or failing the network gives nothing and throws nothing`() = runBlocking {
        assertNull(SideCall.attempt(1_000) { throw HttpException(Response.error<Any>(503, "".toResponseBody())) })
        assertNull(SideCall.attempt(1_000) { throw HttpException(Response.error<Any>(429, "".toResponseBody())) })
        assertNull(SideCall.attempt(1_000) { throw IOException("down") })
        assertNull(SideCall.attempt(1_000) { throw IllegalStateException("bozuk cevap") })
    }

    @Test
    fun `a slow side service times out and a good one returns its answer`() = runBlocking {
        assertNull(SideCall.attempt(50) { delay(5_000); 1 })
        assertEquals(7, SideCall.attempt(1_000) { 7 })
    }

    @Test
    fun `cancellation is not swallowed`() = runBlocking {
        try {
            SideCall.attempt(1_000) { throw CancellationException("iptal") }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
        }
    }
}
