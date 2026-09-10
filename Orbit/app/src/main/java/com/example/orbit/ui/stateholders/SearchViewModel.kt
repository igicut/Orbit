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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F-12 / F-17 / F-29 - the all-events screen.
 *
 * Two sources feed this screen and they are deliberately separate:
 *
 *  - refresh() calls the API and writes whatever the server returns into Room.
 *  - the list itself is read from Room, never from the network response.
 *
 * That indirection is what makes the screen work offline, show locally created
 * events alongside downloaded ones, and update itself the moment anything is
 * written - the UI has one source of truth regardless of where data came from.
 *
 * The radius is applied in both places, on purpose:
 *
 *  - it is sent to the server, so the bounding-box search actually narrows what
 *    is downloaded rather than the app pulling everything and hiding most of it;
 *  - it is applied again over the local list, so changing a chip re-filters
 *    instantly and correctly offline, instead of the screen showing stale
 *    far-away events until a round trip finishes.
 *
 * Applying it only on the server would leave the visible list disagreeing with
 * the chip whenever the network was slow or absent; applying it only locally
 * would make the server's radius parameter decorative.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: EventRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    /** Which of the two lists the Events screen is showing. */
    private val _selectedTab = MutableStateFlow(EventsTab.ALL)
    val selectedTab: StateFlow<EventsTab> = _selectedTab.asStateFlow()

    /**
     * Bookmarked events. Read straight from Room, so this list works with no
     * connection at all - which is the whole point of it.
     *
     * Deliberately not filtered: a bookmark is a decision the user already made,
     * and hiding a saved event because it is 60 km away would be second-guessing
     * them. The filters belong to discovery, not to the shortlist.
     */
    val savedEvents: StateFlow<List<Event>> =
        repository.observeSavedEvents()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** Organiser names by user id, so rows can show who made each event. */
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

    /** F-17 - where the device is, or null if permission is missing or no fix. */
    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Set when the server could not be reached; the cached list still shows. */
    private val _syncError = MutableStateFlow<Int?>(null)
    val syncError: StateFlow<Int?> = _syncError.asStateFlow()

    /**
     * The visible list: Room, narrowed by the filters.
     *
     * All four inputs are combined into one flow, so a change to any of them -
     * a new sync, a chip tap, a location fix arriving - recomputes the list once
     * rather than each triggering its own pass.
     */
    val uiState: StateFlow<UiState<List<Event>>> =
        combine(
            repository.observeEvents(),
            _filters,
            _userLocation,
            userNames,
        ) { events, filters, location, names ->
            events.applyFilters(filters, origin = location, organiserNames = names)
        }
            .map<List<Event>, UiState<List<Event>>> { UiState.Success(it) }
            .catch { emit(UiState.Error(R.string.error_load_events)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    init {
        // Without permission this returns null and the screen simply shows
        // everything, so it is safe to try before the user has been asked.
        refreshLocation()
        refresh()
    }

    fun onQueryChange(value: String) {
        _filters.update { it.copy(query = value) }
    }

    /**
     * A chip was tapped.
     *
     * Changing the radius re-syncs, because a wider one needs events the last
     * download did not ask for. The other filters only narrow what is already
     * cached, so they cost nothing and need no network.
     */
    fun onFiltersChange(value: EventFilters) {
        val previous = _filters.value
        _filters.value = value
        if (value.radius != previous.radius) refresh()
    }

    fun onTabSelected(tab: EventsTab) {
        _selectedTab.value = tab
    }

    fun unsaveEvent(eventId: String) {
        viewModelScope.launch { repository.setEventSaved(eventId, saved = false) }
    }

    /**
     * F-17 - read the device position, then refresh so the radius is measured
     * from where the user actually is.
     *
     * Called again when the UI reports that permission has just been granted.
     */
    fun refreshLocation() {
        viewModelScope.launch {
            val previous = _userLocation.value
            val current = locationProvider.currentLocation()
            _userLocation.value = current

            if (current == null) {
                // Permission refused, or no fix. Any distance-based filter now
                // has nothing to measure from, so drop back to options that
                // still mean something rather than leaving chips selected that
                // silently do nothing.
                _filters.update { filters ->
                    if (!filters.needsLocation) filters
                    else filters.copy(
                        radius = SearchRadius.ANYWHERE,
                        sort = if (filters.sort == EventSort.NEAREST) EventSort.SOONEST
                        else filters.sort,
                    )
                }
            } else if (previous == null) {
                // A first fix changes which events are in range, so the download
                // has to be redone around the new centre.
                refresh()
            }
        }
    }

    /**
     * Pull public events from the server into Room.
     *
     * Centred on the device when a position is known. Without one there is
     * nothing to centre on, so it falls back to a whole-world radius around a
     * fixed point - which returns everything, the only honest answer to "near
     * me" when "me" is unknown.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null

            val location = _userLocation.value
            val radiusKm = _filters.value.radius.km

            try {
                repository.syncPublicEvents(
                    latitude = location?.latitude ?: FALLBACK_LATITUDE,
                    longitude = location?.longitude ?: FALLBACK_LONGITUDE,
                    // No location, or "Anywhere", both mean do not narrow it.
                    radiusKm = if (location == null || radiusKm == null) WORLD_RADIUS_KM
                    else radiusKm,
                )
            } catch (e: Exception) {
                _syncError.value = R.string.search_sync_failed
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private companion object {
        /**
         * Only used as a centre when the device location is unknown, and always
         * together with WORLD_RADIUS_KM - so it selects everything and the exact
         * point does not matter.
         */
        const val FALLBACK_LATITUDE = 44.8125
        const val FALLBACK_LONGITUDE = 20.4612

        /** Half the earth's circumference: any point is inside it. */
        const val WORLD_RADIUS_KM = 20_038.0
    }
}
