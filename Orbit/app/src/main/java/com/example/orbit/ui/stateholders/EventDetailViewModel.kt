package com.example.orbit.ui.stateholders

import kotlinx.coroutines.flow.flowOf

import kotlinx.coroutines.flow.flatMapLatest

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.ExperimentalCoroutinesApi

import com.example.orbit.domain.model.Rating
import com.example.orbit.domain.model.User

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.MutableStateFlow

import com.example.orbit.R

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.location.EventGeofences
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.data.repository.CheckInResult
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.data.repository.RegistrationResult
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.CheckInQr
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
import kotlin.math.roundToInt


@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository,
    private val locationProvider: LocationProvider,
    private val geofences: EventGeofences,
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

    /** Da li sam prijavljen; bira dugme Prijavi se ili Otkazi */
    val isRegistered: StateFlow<Boolean> =
        repository.observeIsRegistered(eventId)
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

    /** F-40: utisci sa servera; prazno dok se ne ucitaju ili bez veze */
    private val _reviews = MutableStateFlow<List<Rating>>(emptyList())
    val reviews: StateFlow<List<Rating>> = _reviews.asStateFlow()

    private val _isReviewPending = MutableStateFlow(false)
    val isReviewPending: StateFlow<Boolean> = _isReviewPending.asStateFlow()

    private val _isRegistrationPending = MutableStateFlow(false)
    val isRegistrationPending: StateFlow<Boolean> = _isRegistrationPending.asStateFlow()

    /** Potvrdjen dolazak otkljucava ocenu */
    val hasAttended: StateFlow<Boolean> =
        repository.observeHasAttended(eventId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    private val _isCheckInPending = MutableStateFlow(false)
    val isCheckInPending: StateFlow<Boolean> = _isCheckInPending.asStateFlow()

    /** Razlog neuspele potvrde; ostaje ispod dugmeta do sledeceg pokusaja */
    private val _checkInProblem = MutableStateFlow<CheckInResult?>(null)
    val checkInProblem: StateFlow<CheckInResult?> = _checkInProblem.asStateFlow()

    /** Spisak za organizatora, ucitava se pri svakom otvaranju panela */
    private val _attendees = MutableStateFlow<UiState<List<Attendee>>>(UiState.Loading)
    val attendees: StateFlow<UiState<List<Attendee>>> = _attendees.asStateFlow()

    /** F-41: tekst QR-a na ulazu, samo za organizatora; ucitava se pri otvaranju panela */
    private val _entryQr = MutableStateFlow<UiState<String>>(UiState.Loading)
    val entryQr: StateFlow<UiState<String>> = _entryQr.asStateFlow()

    /** Prijava ili blokiranje nije uspelo */
    private val _actionError = MutableStateFlow<Int?>(null)
    val actionError: StateFlow<Int?> = _actionError.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteError = MutableStateFlow<Int?>(null)
    val deleteError: StateFlow<Int?> = _deleteError.asStateFlow()

    private val _isCancelling = MutableStateFlow(false)
    val isCancelling: StateFlow<Boolean> = _isCancelling.asStateFlow()

    private val _cancelError = MutableStateFlow<Int?>(null)
    val cancelError: StateFlow<Int?> = _cancelError.asStateFlow()

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
        loadReviews()
    }

    /** F-40: bez veze ostaje poslednja lista; utisci nisu u Room-u */
    fun loadReviews() {
        viewModelScope.launch {
            repository.getReviews(eventId)?.let { _reviews.value = it }
        }
    }

    fun toggleOrganiserBlocked() {
        viewModelScope.launch {
            val ownerId = repository.getEvent(eventId)?.ownerId ?: return@launch
            val done = if (isOrganiserBlocked.value) {
                repository.unblockUser(ownerId)
            } else {
                repository.blockUser(ownerId)
            }
            if (!done) _actionError.value = R.string.error_action_offline
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    /**
     * F-27/F-40: ocena, komentar i slika idu zajedno. Server menja sva tri polja odjednom,
     * pa slanje samo zvezdica bi obrisalo postojeci komentar.
     */
    fun submitReview(value: Int, comment: String, image: String?) {
        if (_isReviewPending.value) return
        viewModelScope.launch {
            _isReviewPending.value = true
            val ok = repository.submitRating(eventId, value, comment, image)
            _isReviewPending.value = false
            _ratingError.value = if (ok) null else R.string.rating_submit_failed
            if (ok) loadReviews()
        }
    }

    /** Prijava ili otkazivanje, prema trenutnom stanju */
    fun toggleRegistration() {
        if (_isRegistrationPending.value) return
        viewModelScope.launch {
            _isRegistrationPending.value = true
            val result = if (isRegistered.value) {
                repository.cancelRegistration(eventId)
            } else {
                repository.registerForEvent(eventId)
            }
            _isRegistrationPending.value = false

            // F-42: zona oko dogadjaja se upisuje ili brise odmah po promeni prijave
            if (result == RegistrationResult.Success) geofences.refresh()

            _actionError.value = when (result) {
                RegistrationResult.Success -> null
                RegistrationResult.Full -> R.string.registration_error_full
                RegistrationResult.Closed -> R.string.registration_error_closed
                RegistrationResult.NoConnection -> R.string.error_action_offline
                RegistrationResult.Failed -> R.string.registration_error_failed
            }
        }
    }

    fun checkIn() {
        if (_isCheckInPending.value) return
        viewModelScope.launch {
            _isCheckInPending.value = true
            _checkInProblem.value = null
            val result = attemptCheckIn()
            _isCheckInPending.value = false
            _checkInProblem.value = result.takeUnless { it == CheckInResult.Success }
        }
    }

    /** Telefon proverava pre slanja zbog tacne poruke; server odlucuje */
    private suspend fun attemptCheckIn(): CheckInResult {
        val event = repository.getEvent(eventId) ?: return CheckInResult.Failed
        val location = locationProvider.preciseLocation() ?: return CheckInResult.NoLocation

        if (location.isMock) return CheckInResult.MockLocation
        if (location.accuracyMeters > AttendanceRules.MAX_ACCURACY_METERS) {
            return CheckInResult.Inaccurate(location.accuracyMeters.roundToInt())
        }
        val distance = AttendanceRules.distanceMeters(event, location)
        if (distance > AttendanceRules.CHECK_IN_RADIUS_METERS) return CheckInResult.TooFar(distance)

        return repository.checkIn(eventId, location)
    }

    /**
     * F-41: potvrda dolaska skeniranim QR-om, kad lokacija ne radi.
     * QR drugog dogadjaja (ili tudji QR) odbija se ovde, pre slanja.
     */
    fun checkInWithQr(scanned: String) {
        if (_isCheckInPending.value) return
        val code = CheckInQr.codeFor(eventId, scanned)
        if (code == null) {
            _checkInProblem.value = CheckInResult.WrongCode
            return
        }
        viewModelScope.launch {
            _isCheckInPending.value = true
            _checkInProblem.value = null
            val result = repository.checkInWithCode(eventId, code)
            _isCheckInPending.value = false
            _checkInProblem.value = result.takeUnless { it == CheckInResult.Success }
        }
    }

    fun onQrScannerUnavailable() {
        _checkInProblem.value = CheckInResult.ScannerUnavailable
    }

    fun onLocationPermissionDenied() {
        _checkInProblem.value = CheckInResult.NoLocation
    }

    fun loadAttendees() {
        viewModelScope.launch {
            _attendees.value = UiState.Loading
            val rows = repository.getAttendees(eventId)
            _attendees.value = if (rows == null) {
                UiState.Error(R.string.attendees_load_failed)
            } else {
                UiState.Success(rows)
            }
        }
    }

    fun loadEntryQr() {
        viewModelScope.launch {
            _entryQr.value = UiState.Loading
            val code = repository.getCheckInCode(eventId)
            _entryQr.value = if (code == null) {
                UiState.Error(R.string.entry_qr_load_failed)
            } else {
                UiState.Success(CheckInQr.text(eventId, code))
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }

    /** F-39: otkazivanje; uspeh osvezi kes, pa ekran sam pokaze oznaku */
    fun cancelEvent(reason: String?) {
        if (_isCancelling.value) return
        viewModelScope.launch {
            _isCancelling.value = true
            _cancelError.value = null
            val ok = repository.cancelEvent(eventId, reason)
            _isCancelling.value = false
            if (!ok) _cancelError.value = R.string.detail_cancel_failed
        }
    }

    fun clearCancelError() {
        _cancelError.value = null
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
