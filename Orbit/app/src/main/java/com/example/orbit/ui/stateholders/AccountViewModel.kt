package com.example.orbit.ui.stateholders

import com.example.orbit.data.notification.EventNotifier
import com.example.orbit.data.notification.ReminderOutcome
import com.example.orbit.data.notification.ReminderResults

import com.example.orbit.data.local.dao.BlockedUserRow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.R
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.data.repository.JoinResult
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The account screen: events created here, plus private events joined by code.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: EventRepository,
    currentUser: CurrentUser,
    private val notifier: EventNotifier,
    reminderResults: ReminderResults,
) : ViewModel() {

    val userId: String = currentUser.id

    /** F-26 - whether notifications may be shown at all (Android 13+). */
    fun canPostNotifications(): Boolean = notifier.hasPermission()

    /**
     * F-25 - what the last reminder check did, so the screen can say so.
     *
     * The check runs in a service and used to end in silence, which made "no
     * events were due" indistinguishable from "it never ran". Passing the
     * outcome through means every press of the button produces an answer.
     */
    val reminderOutcome: SharedFlow<ReminderOutcome> = reminderResults.outcomes

    // ---- F-13: display name ---------------------------------------------

    private val _displayName = MutableStateFlow(currentUser.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    /** The name last published, so the Save button knows there is a change. */
    private val _savedDisplayName = MutableStateFlow(currentUser.displayName)
    val savedDisplayName: StateFlow<String> = _savedDisplayName.asStateFlow()

    private val _nameError = MutableStateFlow<Int?>(null)
    val nameError: StateFlow<Int?> = _nameError.asStateFlow()

    fun onDisplayNameChange(value: String) {
        _displayName.value = value.take(MAX_NAME_LENGTH)
        _nameError.value = null
    }

    fun saveDisplayName() {
        val name = _displayName.value.trim()
        if (name.isBlank()) {
            _nameError.value = R.string.account_name_required
            return
        }

        viewModelScope.launch {
            val published = repository.updateDisplayName(name)
            _savedDisplayName.value = name
            _displayName.value = name
            // The name is stored locally either way; this only reports whether
            // other people can see it yet.
            _nameError.value = if (published) null else R.string.account_name_offline
        }
    }

    val uiState: StateFlow<UiState<List<Event>>> =
        repository.observeEventsByOwner(userId)
            .map<List<Event>, UiState<List<Event>>> { UiState.Success(it) }
            .catch { emit(UiState.Error(R.string.error_load_your_events)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading,
            )

    /** F-21 - private events reached by code, so not owned by this device. */
    val joinedEvents: StateFlow<List<Event>> =
        repository.observeJoinedPrivateEvents(userId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // ---- join-by-code dialog -------------------------------------------

    private val _showJoinDialog = MutableStateFlow(false)
    val showJoinDialog: StateFlow<Boolean> = _showJoinDialog.asStateFlow()

    private val _joinCode = MutableStateFlow("")
    val joinCode: StateFlow<String> = _joinCode.asStateFlow()

    private val _joinError = MutableStateFlow<Int?>(null)
    val joinError: StateFlow<Int?> = _joinError.asStateFlow()

    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining.asStateFlow()

/**
     * Title of the event just joined, for a one-off confirmation message.
     *
     * The screen clears it after showing the toast - otherwise coming back to
     * this tab would show the same message again.
     */
    private val _joinedEventTitle = MutableStateFlow<String?>(null)
    val joinedEventTitle: StateFlow<String?> = _joinedEventTitle.asStateFlow()

    /** F-28 - people this device has blocked, with names where known. */
    val blockedUsers: StateFlow<List<BlockedUserRow>> =
        repository.observeBlockedUsers()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun unblock(userId: String) {
        viewModelScope.launch { repository.unblockUser(userId) }
    }

    /** Organiser names by user id, for the joined-events list. */
    val userNames: StateFlow<Map<String, String>> =
        repository.observeUserNames()
            .catch { emit(emptyMap()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun openJoinDialog() {
        _joinCode.value = ""
        _joinError.value = null
        _showJoinDialog.value = true
    }

    fun dismissJoinDialog() {
        _showJoinDialog.value = false
    }

    fun onJoinCodeChange(value: String) {
        // Codes are uppercase and six characters; doing this as the user types
        // means the field cannot hold something that could never match.
        _joinCode.value = value.uppercase().filter { it.isLetterOrDigit() }.take(ACCESS_CODE_LENGTH)
        _joinError.value = null
    }

    fun join() {
        val code = _joinCode.value
        if (code.isBlank()) {
            _joinError.value = R.string.join_error_not_found
            return
        }

        viewModelScope.launch {
            _isJoining.value = true
            when (val result = repository.joinEventByAccessCode(code)) {
                is JoinResult.Success -> {
                    _showJoinDialog.value = false
                    _joinedEventTitle.value = result.eventTitle
                }
                JoinResult.NotFound -> _joinError.value = R.string.join_error_not_found
                JoinResult.NetworkError -> _joinError.value = R.string.join_error_network
            }
            _isJoining.value = false
        }
    }

    fun onJoinMessageShown() {
        _joinedEventTitle.value = null
    }

    private companion object {
        const val ACCESS_CODE_LENGTH = 6
        const val MAX_NAME_LENGTH = 40
    }
}
