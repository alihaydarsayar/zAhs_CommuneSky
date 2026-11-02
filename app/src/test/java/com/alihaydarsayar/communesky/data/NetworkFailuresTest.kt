package com.alihaydarsayar.communesky.data

import com.alihaydarsayar.communesky.data.remote.FailureSink
import com.alihaydarsayar.communesky.data.remote.NetworkFailure
import com.alihaydarsayar.communesky.data.remote.NetworkFailureInterceptor
import com.alihaydarsayar.communesky.data.remote.NetworkFailureList
import com.alihaydarsayar.communesky.data.remote.RetryPolicy
import com.alihaydarsayar.communesky.data.remote.ServiceName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** 503/429 yeniden deneme, hata kaydı ve kaydın içeriği. */
class NetworkFailuresTest {

    private val day = 24 * 3600 * 1000L

    @Test
    fun `retry waits grow and stop after the limit`() {
        assertEquals(1_000L, RetryPolicy.delayMs(0, 503, null))
        assertEquals(2_000L, RetryPolicy.delayMs(1, 503, null))
        assertNull(RetryPolicy.delayMs(2, 503, null))
        assertEquals(1_000L, RetryPolicy.delayMs(0, 429, null))
    }

    @Test
    fun `only 429 and server errors are retried`() {
        assertNull(RetryPolicy.delayMs(0, 404, null))
        assertNull(RetryPolicy.delayMs(0, 400, null))
        assertNull(RetryPolicy.delayMs(0, 200, null))
        assertTrue(RetryPolicy.retryable(500) && RetryPolicy.retryable(504) && RetryPolicy.retryable(429))
    }

    @Test
    fun `Retry-After is respected when short and not waited for when long`() {
        assertEquals(4_000L, RetryPolicy.delayMs(0, 429, 4))
        assertNull(RetryPolicy.delayMs(0, 429, 30))
    }

    @Test
    fun `the list keeps the last five, newest first, and drops anything older than seven days`() {
        var list = emptyList<NetworkFailure>()
        val now = 100 * day
        for (i in 1..7) list = NetworkFailureList.add(list, NetworkFailure(now - (8 - i) * 1000L, "api.open-meteo.com", 503, null), now)
        assertEquals(5, list.size)
        assertEquals(now - 1000L, list.first().at)
        assertTrue(NetworkFailureList.add(emptyList(), NetworkFailure(now - 8 * day, "x.com", 500, null), now).isEmpty())
    }

    @Test
    fun `encoding round trips and never carries more than host, code, time and Retry-After`() {
        val list = listOf(NetworkFailure(5, "api.open-meteo.com", 503, 12), NetworkFailure(4, "servis.mgm.gov.tr", 0, null))
        assertEquals(list, NetworkFailureList.decode(NetworkFailureList.encode(list)))
        // Yol, sorgu ya da yer bilgisi içeren bir "host" kaydedilmez.
        assertEquals("other", NetworkFailureList.cleanHost("api.open-meteo.com/v1/forecast?latitude=41.04"))
        assertEquals("other", NetworkFailureList.cleanHost("Besiktas, Istanbul"))
        assertTrue(NetworkFailureList.decode("garbage;1,2").isEmpty())
    }

    @Test
    fun `services have a readable name`() {
        assertEquals("Open-Meteo", ServiceName.of("api.open-meteo.com"))
        assertEquals("Open-Meteo", ServiceName.of("air-quality-api.open-meteo.com"))
        assertEquals("MGM", ServiceName.of("servis.mgm.gov.tr"))
        assertEquals("NOAA", ServiceName.of("aviationweather.gov"))
        assertEquals("Photon", ServiceName.of("photon.komoot.io"))
        assertNull(ServiceName.of(null))
    }

    private class Sink : FailureSink {
        val failures = mutableListOf<Triple<String, Int, Int?>>()
        var successes = 0
        override fun record(host: String, code: Int, retryAfterSeconds: Int?) {
            failures += Triple(host, code, retryAfterSeconds)
        }

        override fun markSuccess() {
            successes++
        }
    }

    private fun client(sink: Sink, answers: ArrayDeque<Any>, calls: IntArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(NetworkFailureInterceptor(sink) { attempt, code, retryAfter -> RetryPolicy.delayMs(attempt, code, retryAfter)?.let { 1L } })
        .addInterceptor(Interceptor { chain ->
            calls[0]++
            when (val answer = answers.removeFirst()) {
                is IOException -> throw answer
                is Pair<*, *> -> response(chain.request(), answer.first as Int, answer.second as String?)
                else -> error("?")
            }
        })
        .build()

    private fun response(request: Request, code: Int, retryAfter: String?): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
        .body("".toResponseBody(null)).build()

    private fun get(client: OkHttpClient, url: String = "https://api.open-meteo.com/v1/forecast?latitude=1&longitude=2") =
        client.newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun `a 503 is retried and a later success is not recorded as a failure`() {
        val sink = Sink()
        val calls = intArrayOf(0)
        val result = get(client(sink, ArrayDeque(listOf(503 to null, 503 to null, 200 to null)), calls))
        assertEquals(200, result.code)
        assertEquals(3, calls[0])
        assertTrue(sink.failures.isEmpty())
        assertEquals(1, sink.successes)
    }

    @Test
    fun `a 503 that keeps coming is recorded once with host, code and Retry-After`() {
        val sink = Sink()
        val calls = intArrayOf(0)
        val result = get(client(sink, ArrayDeque(listOf(503 to "2", 503 to "2", 503 to "2")), calls))
        assertEquals(503, result.code)
        assertEquals(3, calls[0])
        assertEquals(listOf(Triple("api.open-meteo.com", 503, 2)), sink.failures)
    }

    @Test
    fun `a long Retry-After is not waited for and is recorded at once`() {
        val sink = Sink()
        val calls = intArrayOf(0)
        val result = get(client(sink, ArrayDeque(listOf(429 to "600")), calls))
        assertEquals(429, result.code)
        assertEquals(1, calls[0])
        assertEquals(listOf(Triple("api.open-meteo.com", 429, 600)), sink.failures)
    }

    @Test
    fun `a client error is neither retried nor recorded and a lost connection is recorded as code 0`() {
        val sink = Sink()
        val calls = intArrayOf(0)
        assertEquals(404, get(client(sink, ArrayDeque(listOf(404 to null)), calls)).code)
        assertEquals(1, calls[0])
        assertTrue(sink.failures.isEmpty())
        try {
            get(client(sink, ArrayDeque(listOf(IOException("down"))), intArrayOf(0)), "https://servis.mgm.gov.tr/web/x?lat=1")
        } catch (_: IOException) {
        }
        assertEquals(listOf(Triple("servis.mgm.gov.tr", 0, null)), sink.failures)
    }

    @Test
    fun `only the main forecast marks a success`() {
        val sink = Sink()
        get(client(sink, ArrayDeque(listOf(200 to null)), intArrayOf(0)), "https://api.open-meteo.com/v1/forecast?models=ecmwf_ifs&latitude=1")
        get(client(sink, ArrayDeque(listOf(200 to null)), intArrayOf(0)), "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=1")
        assertEquals(0, sink.successes)
    }
}
