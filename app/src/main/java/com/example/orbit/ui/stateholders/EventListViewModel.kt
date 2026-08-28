package com.example.orbit.ui.stateholders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class EventListViewModel @Inject constructor(
    repository: EventRepository,
    ) : ViewModel() {

    val uiState: StateFlow<UiState<List<Event>>> =
        repository.observeEvents()
            .map<List<Event>, UiState<List<Event>>> { events -> UiState.Success(events) }
            .catch { error -> emit(UiState.Error(error.message ?: "Could not load events")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )
}
