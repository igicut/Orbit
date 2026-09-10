package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.R
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F-17 / F-18 - the map screen: events plus where the user is.
 *
 * The ViewModel never asks for permission. It is told that permission exists and
 * responds by fetching a position; asking needs an Activity and belongs to the UI.
 */
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

    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    /**
     * Set when permission was granted but a position still could not be read -
     * location services off, or no fix yet. The map still shows; only the
     * "you are here" marker is missing.
     */
    private val _locationNotice = MutableStateFlow<Int?>(null)
    @StringRes
    val locationNotice: StateFlow<Int?> = _locationNotice.asStateFlow()

    fun refreshLocation() {
        viewModelScope.launch {
            val location = locationProvider.currentLocation()
            _userLocation.value = location
            _locationNotice.value = if (location == null) R.string.location_no_fix else null
        }
    }
}
