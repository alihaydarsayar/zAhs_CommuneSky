package com.alihaydarsayar.communesky.ui.places

import com.alihaydarsayar.communesky.data.search.SearchOutcome
import com.alihaydarsayar.communesky.model.PlaceSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed interface SearchState {
    /** Arama kutusu boş ya da çok kısa: kayıtlı yerler gösterilir. */
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Results(val query: String, val results: List<PlaceSearchResult>) : SearchState
    data class Failed(val query: String) : SearchState
}

/**
 * Yazarken arama: kullanıcı yazmayı [debounceMillis] kadar bırakınca aranır; her harfte istek
 * atılmaz. Yeni harf gelince süren arama iptal edilir (flatMapLatest), eski sonuç yenisinin
 * üstüne yazılmaz. Kutu silinince bekleme olmadan hemen kayıtlı yerlere dönülür.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun Flow<String>.searchAsYouType(
    debounceMillis: Long = 350,
    minLength: Int = 2,
    search: suspend (String) -> SearchOutcome,
): Flow<SearchState> = map { it.trim() }
    .debounce { query -> if (query.length < minLength) 0 else debounceMillis }
    .distinctUntilChanged()
    .flatMapLatest { query ->
        if (query.length < minLength) {
            flowOf<SearchState>(SearchState.Idle)
        } else {
            flow<SearchState> {
                emit(SearchState.Loading(query))
                emit(
                    when (val outcome = search(query)) {
                        is SearchOutcome.Found -> SearchState.Results(query, outcome.results)
                        SearchOutcome.Failed -> SearchState.Failed(query)
                    },
                )
            }
        }
    }
