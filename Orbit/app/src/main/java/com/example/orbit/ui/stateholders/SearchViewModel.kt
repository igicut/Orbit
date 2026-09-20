package com.example.orbit.ui.stateholders

import com.example.orbit.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.domain.model.EventSort
import com.example.orbit.domain.model.SearchRadius
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.domain.model.applyFilters
import com.example.orbit.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** F-12/F-17/F-29: lista dogadjaja; server upisuje u Room, UI cita Room */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: EventRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    /** Imena organizatora po id-ju, za redove liste */
    val userNames: StateFlow<Map<String, String>> =
        repository.observeUserNames()
            .catch { emit(emptyMap()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    private val _filters = MutableStateFlow(EventFilters())
    val filters: StateFlow<EventFilters> = _filters.asStateFlow()

    /** F-17: pozicija uredjaja, null bez dozvole ili lokacije */
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    /** Otkazuje se pri svakom novom slovu */
    private var semanticSearchJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Server nedostupan, kesirana lista i dalje vidljiva */
    private val _syncError = MutableStateFlow<Int?>(null)
    val syncError: StateFlow<Int?> = _syncError.asStateFlow()

    /** F-32: skorovi poslednje semanticke pretrage, prazno bez upita ili bez servera */
    private val _relevance = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Vidljiva lista: Room plus filteri, u jednom combine */
    val uiState: StateFlow<UiState<List<Event>>> =
        combine(
            repository.observeEvents(),
            _filters,
            _userLocation,
            userNames,
            _relevance,
        ) { events, filters, location, names, relevance ->
            events.applyFilters(
                filters,
                origin = location,
                organiserNames = names,
                relevance = relevance,
            )
        }
            .map<List<Event>, UiState<List<Event>>> { UiState.Success(it) }
            .catch { emit(UiState.Error(R.string.error_load_events)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    init {
        // Bez dozvole vraca null, bezbedno je pozvati odmah
        refreshLocation()
        refresh()
    }

    fun onQueryChange(value: String) {
        _filters.update { it.copy(query = value) }
        scheduleSemanticSearch(value.trim())
    }

    /**
     * F-32: pita server tek kad kucanje stane, i samo za upit koji lici na recenicu.
     * Neuspeh ostavlja praznu mapu, pa lista ostaje na pretrazi po recima.
     */
    private fun scheduleSemanticSearch(query: String) {
        semanticSearchJob?.cancel()

        if (query.length < MIN_SEMANTIC_QUERY_LENGTH) {
            _relevance.value = emptyMap()
            return
        }

        semanticSearchJob = viewModelScope.launch {
            delay(SEMANTIC_SEARCH_DEBOUNCE_MS)

            val location = _userLocation.value
            _relevance.value = repository.semanticSearch(
                query = query,
                latitude = location?.latitude ?: FALLBACK_LATITUDE,
                longitude = location?.longitude ?: FALLBACK_LONGITUDE,
                radiusKm = if (location == null) null else _filters.value.radius.km,
            )
        }
    }

    /** Promena radijusa ponovo sinhronizuje, ostali filteri ne */
    fun onFiltersChange(value: EventFilters) {
        val previous = _filters.value
        _filters.value = value
        if (value.radius != previous.radius) refresh()
    }

    /** F-17: procitaj poziciju pa osvezi oko nje */
    fun refreshLocation() {
        viewModelScope.launch {
            val previous = _userLocation.value
            val current = locationProvider.currentLocation()
            _userLocation.value = current

            if (current == null) {
                // Nema lokacije: vracamo filtere koji rade bez nje
                _filters.update { filters ->
                    if (!filters.needsLocation) filters
                    else filters.copy(
                        radius = SearchRadius.ANYWHERE,
                        sort = if (filters.sort == EventSort.NEAREST) EventSort.SOONEST
                        else filters.sort,
                    )
                }
            } else if (previous == null) {
                // Prva lokacija menja sta je u dometu, osvezi
                refresh()
            }
        }
    }

    /** Skida javne dogadjaje; bez lokacije ne salje radijus */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null

            val location = _userLocation.value
            val radiusKm = _filters.value.radius.km

            try {
                // Prvo nalog: prijavljeni ostaju u kesu i van radijusa
                val accountSynced = repository.syncAccountData()
                repository.syncPublicEvents(
                    latitude = location?.latitude ?: FALLBACK_LATITUDE,
                    longitude = location?.longitude ?: FALLBACK_LONGITUDE,
                    // Nema lokacije ili Anywhere: bez suzavanja
                    radiusKm = if (location == null) null else radiusKm,
                )
                if (!accountSynced) _syncError.value = R.string.search_sync_failed
            } catch (e: Exception) {
                _syncError.value = R.string.search_sync_failed
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private companion object {
        /** F-32: jedan poziv po pauzi u kucanju, ne po slovu */
        const val SEMANTIC_SEARCH_DEBOUNCE_MS = 400L

        /** Krace od ovoga je pocetak reci, ne pitanje */
        const val MIN_SEMANTIC_QUERY_LENGTH = 3

        /** Server trazi lat/lng, bez radijusa tacka nije bitna */
        const val FALLBACK_LATITUDE = 44.8125
        const val FALLBACK_LONGITUDE = 20.4612
    }
}
