package com.alihaydarsayar.communesky.ui.places

import com.alihaydarsayar.communesky.data.search.SearchOutcome
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchAsYouTypeTest {

    private fun result(name: String) = PlaceSearchResult(name, null, "TR", 0.0, 0.0)

    @Test
    fun `typing quickly searches only once, after the pause`() = runTest {
        val queries = MutableStateFlow("")
        val searched = mutableListOf<String>()
        val states = mutableListOf<SearchState>()
        val job = launch {
            queries.searchAsYouType(debounceMillis = 350) { query ->
                searched += query
                SearchOutcome.Found(listOf(result(query)))
            }.collect { states += it }
        }
        for (text in listOf("A", "At", "Ata", "Atak", "Ataku", "Atakum")) {
            queries.value = text
            advanceTimeBy(100)
        }
        advanceUntilIdle()

        assertEquals(listOf("Atakum"), searched)
        assertEquals(SearchState.Results("Atakum", listOf(result("Atakum"))), states.last())
        job.cancel()
    }

    @Test
    fun `a new letter cancels the search in progress`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<SearchState>()
        val job = launch {
            queries.searchAsYouType(debounceMillis = 350) { query ->
                delay(1_000) // yavaş ağ
                SearchOutcome.Found(listOf(result(query)))
            }.collect { states += it }
        }
        queries.value = "Tuz"
        advanceTimeBy(500) // arama başladı, henüz bitmedi
        queries.value = "Tuzla"
        advanceUntilIdle()

        val results = states.filterIsInstance<SearchState.Results>()
        assertEquals(listOf("Tuzla"), results.map { it.query })
        job.cancel()
    }

    @Test
    fun `clearing the box returns to idle without waiting`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<SearchState>()
        val job = launch {
            queries.searchAsYouType(debounceMillis = 350) { SearchOutcome.Found(emptyList()) }
                .collect { states += it }
        }
        queries.value = "Moda"
        advanceUntilIdle()
        queries.value = ""
        runCurrent()

        assertEquals(SearchState.Idle, states.last())
        job.cancel()
    }

    @Test
    fun `one letter or spaces do not search`() = runTest {
        val queries = MutableStateFlow("")
        var calls = 0
        val job = launch {
            queries.searchAsYouType(minLength = 2) { calls++; SearchOutcome.Found(emptyList()) }.collect { }
        }
        queries.value = "A"
        advanceUntilIdle()
        queries.value = "  A  "
        advanceUntilIdle()
        assertEquals(0, calls)
        job.cancel()
    }

    @Test
    fun `failure is reported`() = runTest {
        val queries = MutableStateFlow("")
        val states = mutableListOf<SearchState>()
        val job = launch {
            queries.searchAsYouType { SearchOutcome.Failed }.collect { states += it }
        }
        queries.value = "Kadıköy"
        advanceUntilIdle()
        assertEquals(SearchState.Failed("Kadıköy"), states.last())
        job.cancel()
    }
}
