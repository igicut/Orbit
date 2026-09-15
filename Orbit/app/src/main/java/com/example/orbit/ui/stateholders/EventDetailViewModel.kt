package com.example.orbit.ui.stateholders

import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.flow.flatMapLatest

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.ExperimentalCoroutinesApi

import com.example.orbit.domain.model.User

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.MutableStateFlow

import com.example.orbit.R

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


@OptIn(ExperimentalCoroutinesApi::class)
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
            .catch { emit(UiState.Error(R.string.error_load_event)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    /** Da li je sacuvan; menja ikonicu gore */
    val isSaved: StateFlow<Boolean> =
        repository.observeIsEventSaved(eventId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /** F-27: moja ocena, null ako nisam ocenio */
    val myRating: StateFlow<Int?> =
        repository.observeMyRating(eventId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    private val _ratingError = MutableStateFlow<Int?>(null)
    val ratingError: StateFlow<Int?> = _ratingError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteError = MutableStateFlow<Int?>(null)
    val deleteError: StateFlow<Int?> = _deleteError.asStateFlow()

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted.asStateFlow()

    /** F-28: organizator, za ime i blokiranje */
    val organiser: StateFlow<User?> =
        uiState
            .map { (it as? UiState.Success)?.data?.ownerId }
            .distinctUntilChanged()
            .flatMapLatest { ownerId ->
                if (ownerId == null) flowOf(null) else repository.observeUser(ownerId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    val isOrganiserBlocked: StateFlow<Boolean> =
        uiState
            .map { (it as? UiState.Success)?.data?.ownerId }
            .distinctUntilChanged()
            .flatMapLatest { ownerId ->
                if (ownerId == null) flowOf(false) else repository.observeIsBlocked(ownerId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    init {
        // Jednom preuzmi profil organizatora, greske se ignorisu
        viewModelScope.launch {
            val ownerId = repository.getEvent(eventId)?.ownerId ?: return@launch
            if (ownerId != currentUserId) repository.cacheUser(ownerId)
        }
    }

    fun toggleOrganiserBlocked() {
        viewModelScope.launch {
            val ownerId = repository.getEvent(eventId)?.ownerId ?: return@launch
            if (isOrganiserBlocked.value) {
                repository.unblockUser(ownerId)
            } else {
                repository.blockUser(ownerId)
            }
        }
    }

    fun submitRating(value: Int) {
        viewModelScope.launch {
            val ok = repository.submitRating(eventId, value)
            _ratingError.value = if (ok) null else R.string.rating_submit_failed
        }
    }

    fun toggleSaved() {
        viewModelScope.launch {
            repository.setEventSaved(eventId, saved = !isSaved.value)
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }

    fun delete() {
        if (_isDeleting.value || _isDeleted.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            _deleteError.value = null
            val deleted = repository.deleteEvent(eventId)
            _isDeleting.value = false
            if (deleted) {
                _isDeleted.value = true
            } else {
                _deleteError.value = R.string.detail_delete_failed
            }
        }
    }
}
