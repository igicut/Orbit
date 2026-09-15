package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.R
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

/** F-17/F-18: dogadjaji i pozicija; dozvolu trazi UI */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    repository: EventRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    val uiState: StateFlow<UiState<List<Event>>> =
        repository.observeEvents()
            .map<List<Event>, UiState<List<Event>>> { UiState.Success(it) }
            .catch { emit(UiState.Error(R.string.error_load_events)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    private val locationEnabled = MutableStateFlow(false)

    /** Pozicija uzivo, GPS radi samo dok je mapa na ekranu */
    val userLocation: StateFlow<UserLocation?> =
        locationEnabled
            .flatMapLatest { enabled ->
                if (enabled) locationProvider.locationUpdates() else emptyFlow()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** F-18: id otvorene kartice, prezivljava rotaciju */
    private val _selectedEventId = MutableStateFlow<String?>(null)

    /** Dogadjaj za karticu, trazi se po id-ju u listi */
    val selectedEvent: StateFlow<Event?> =
        combine(uiState, _selectedEventId) { state, id ->
            if (id == null) return@combine null
            val loaded = (state as? UiState.Success)?.data.orEmpty()
            loaded.firstOrNull { it.id == id }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Poruka kad nema pozicije, sa zadrskom od par sekundi */
    @StringRes
    val locationNotice: StateFlow<Int?> =
        combine(locationEnabled, userLocation) { enabled, location -> enabled && location == null }
            .transformLatest { waitingForFix ->
                emit(null)
                if (waitingForFix) {
                    delay(NO_FIX_NOTICE_DELAY_MS)
                    emit(R.string.location_no_fix)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    fun onMarkerSelected(eventId: String) {
        _selectedEventId.value = eventId
    }

    fun dismissPreview() {
        _selectedEventId.value = null
    }

    fun startLocationUpdates() {
        locationEnabled.value = true
    }

    private companion object {
        const val NO_FIX_NOTICE_DELAY_MS = 10_000L
    }
}
