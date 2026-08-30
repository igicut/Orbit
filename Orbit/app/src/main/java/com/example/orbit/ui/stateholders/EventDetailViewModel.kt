package com.example.orbit.ui.stateholders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.navigation.OrbitDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository,
    currentUser: CurrentUser,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: String =
        checkNotNull(savedStateHandle[OrbitDestinations.EVENT_ID_ARG]) {
            "EventDetailScreen was opened without an eventId"
        }

    val currentUserId: String = currentUser.id

    val uiState: StateFlow<UiState<Event?>> =
        repository.observeEvent(eventId)
            .map<Event?, UiState<Event?>> { event -> UiState.Success(event) }
            .catch { error -> emit(UiState.Error(error.message ?: "Could not load event")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    fun delete() {
        viewModelScope.launch { repository.deleteEvent(eventId) }
    }
}
