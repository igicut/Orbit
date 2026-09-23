package com.example.orbit.ui.stateholders

import com.example.orbit.data.notification.EventNotifier
import com.example.orbit.data.notification.ReminderOutcome
import com.example.orbit.data.notification.ReminderResults

import com.example.orbit.data.local.dao.BlockedUserRow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.R
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.location.EventGeofences
import com.example.orbit.data.repository.AuthRepository
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

/** Nalog: moji dogadjaji i privatni pridruzeni kodom */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: EventRepository,
    private val authRepository: AuthRepository,
    currentUser: CurrentUser,
    private val notifier: EventNotifier,
    private val geofences: EventGeofences,
    reminderResults: ReminderResults,
) : ViewModel() {

    val userId: String = currentUser.id

    val email: String = currentUser.email

    /** F-13: MainActivity posle ovoga prikazuje prijavu */
    fun logOut() {
        viewModelScope.launch {
            authRepository.logOut()
            // F-42: posle odjave nema prijava, pa se zone oko dogadjaja brisu
            geofences.refresh()
        }
    }

    /** F-26: da li smemo da prikazemo obavestenja (Android 13+) */
    fun canPostNotifications(): Boolean = notifier.hasPermission()

    /** F-42: da li automatska potvrda dolaska ima sve dozvole */
    fun hasAutoCheckIn(): Boolean = geofences.hasPermission()

    /** F-42: posle dobijene dozvole zone se upisuju bez cekanja na sledece pokretanje */
    fun refreshGeofences() {
        viewModelScope.launch { geofences.refresh() }
    }

    /** F-25: ishod poslednje provere podsetnika za ekran */
    val reminderOutcome: SharedFlow<ReminderOutcome> = reminderResults.outcomes

    // ---- F-13: ime za prikaz ----

    private val _displayName = MutableStateFlow(currentUser.displayName)
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    /** Poslednje objavljeno ime, za dugme Sacuvaj */
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
            // Ime se uvek cuva lokalno; ovo javlja da li je objavljeno
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

    /** Broj potvrdjenih dolazaka, za zaglavlje naloga */
    val attendedCount: StateFlow<Int> =
        repository.observeAttendedEvents()
            .map { it.size }
            .catch { emit(0) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /** F-21: privatni dogadjaji dobijeni kodom, nisu moji */
    val joinedEvents: StateFlow<List<Event>> =
        repository.observeJoinedPrivateEvents(userId)
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // ---- dijalog za unos koda ----

    private val _showJoinDialog = MutableStateFlow(false)
    val showJoinDialog: StateFlow<Boolean> = _showJoinDialog.asStateFlow()

    private val _joinCode = MutableStateFlow("")
    val joinCode: StateFlow<String> = _joinCode.asStateFlow()

    private val _joinError = MutableStateFlow<Int?>(null)
    val joinError: StateFlow<Int?> = _joinError.asStateFlow()

    private val _isJoining = MutableStateFlow(false)
    val isJoining: StateFlow<Boolean> = _isJoining.asStateFlow()

/** Naslov upravo pridruzenog dogadjaja, za jednokratnu poruku */
    private val _joinedEventTitle = MutableStateFlow<String?>(null)
    val joinedEventTitle: StateFlow<String?> = _joinedEventTitle.asStateFlow()

    /** F-28: blokirani korisnici, sa imenima gde ih znamo */
    val blockedUsers: StateFlow<List<BlockedUserRow>> =
        repository.observeBlockedUsers()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** Odblokiranje nije stiglo do servera */
    private val _actionError = MutableStateFlow<Int?>(null)
    val actionError: StateFlow<Int?> = _actionError.asStateFlow()

    fun unblock(userId: String) {
        viewModelScope.launch {
            val done = repository.unblockUser(userId)
            if (!done) _actionError.value = R.string.error_action_offline
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    /** Imena organizatora po id-ju, za listu pridruzenih */
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
        // Kodovi su uppercase i sest znakova, cistimo pri kucanju
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
