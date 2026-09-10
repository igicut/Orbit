package com.example.orbit.ui.stateholders

import com.example.orbit.ui.navigation.OrbitDestinations

import com.example.orbit.domain.model.EventEditRules

import androidx.lifecycle.SavedStateHandle

import kotlinx.coroutines.CoroutineScope

import com.example.orbit.R

import androidx.annotation.StringRes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random


data class CreateEventFormState(
    val title: String = "",
    val description: String = "",
    val category: EventCategory = EventCategory.OTHER,
    val visibility: Visibility = Visibility.PUBLIC,
    val startTime: Long? = null,
    val latitude: String = "",
    val longitude: String = "",
    val address: String = "",
    val capacity: String = "",
    val price: String = "",
    val requiresReservation: Boolean = false,
    val imageUris: List<String> = emptyList(),

    // filled in by validate(); null means "this field is fine"
    @StringRes val titleError: Int? = null,
    @StringRes val descriptionError: Int? = null,
    @StringRes val startTimeError: Int? = null,
    @StringRes val latitudeError: Int? = null,
    @StringRes val longitudeError: Int? = null,

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedAccessCode: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val currentUser: CurrentUser,
    /**
     * Application-scoped, deliberately not viewModelScope.
     *
     * The screen closes the instant the event is stored locally, which cancels
     * viewModelScope - the upload would die halfway. This scope outlives the
     * screen, so the push finishes in the background either way.
     */
    private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating; the event being changed when editing. */
    private val editingId: String? = savedStateHandle[OrbitDestinations.EVENT_ID_ARG]

    /**
     * The event as it was before editing.
     *
     * Kept because every limit is relative to the original - how far it moved,
     * not where it ended up.
     */
    private var original: Event? = null

    init {
        if (editingId != null) loadForEditing(editingId)
    }

    private fun loadForEditing(id: String) {
        viewModelScope.launch {
            val event = repository.getEvent(id) ?: return@launch
            original = event
            _state.value = CreateEventFormState(
                title = event.title,
                description = event.description,
                category = event.category,
                visibility = event.visibility,
                startTime = event.startTime,
                latitude = event.latitude.toString(),
                longitude = event.longitude.toString(),
                address = event.address.orEmpty(),
                capacity = event.capacity?.toString().orEmpty(),
                price = event.price?.toString().orEmpty(),
                requiresReservation = event.requiresReservation,
                imageUris = event.imageUris,
                isEditing = true,
            )
        }
    }

    private val _state = MutableStateFlow(CreateEventFormState())
    val state: StateFlow<CreateEventFormState> = _state.asStateFlow()


    fun onTitleChange(value: String) = _state.update { it.copy(title = value, titleError = null) }
    fun onDescriptionChange(value: String) =
        _state.update { it.copy(description = value, descriptionError = null) }
    fun onCategoryChange(value: EventCategory) = _state.update { it.copy(category = value) }
    fun onVisibilityChange(value: Visibility) = _state.update { it.copy(visibility = value) }
    fun onStartTimeChange(value: Long) =
        _state.update { it.copy(startTime = value, startTimeError = null) }
    fun onLatitudeChange(value: String) =
        _state.update { it.copy(latitude = value, latitudeError = null) }
    fun onLongitudeChange(value: String) =
        _state.update { it.copy(longitude = value, longitudeError = null) }
    fun onAddressChange(value: String) = _state.update { it.copy(address = value) }
    fun onCapacityChange(value: String) = _state.update { it.copy(capacity = value) }
    fun onPriceChange(value: String) = _state.update { it.copy(price = value) }
    fun onRequiresReservationChange(value: Boolean) =
        _state.update { it.copy(requiresReservation = value) }
    fun onImagesPicked(uris: List<String>) =
        _state.update { it.copy(imageUris = (it.imageUris + uris).distinct()) }
    fun onImageRemoved(uri: String) =
        _state.update { it.copy(imageUris = it.imageUris - uri) }

    private fun validate(): Boolean {
        val current = _state.value

        val titleError = if (current.title.isBlank()) R.string.validation_title_required else null
        val descriptionError =
            if (current.description.isBlank()) R.string.validation_description_required else null

        val startTimeError = when {
            current.startTime == null -> R.string.validation_start_time_required
            current.startTime <= System.currentTimeMillis() -> R.string.validation_start_time_future
            else -> null
        }

        val lat = current.latitude.toDoubleOrNull()
        val latitudeError = when {
            current.latitude.isBlank() -> R.string.validation_latitude_required
            lat == null -> R.string.validation_latitude_number
            lat < -90.0 || lat > 90.0 -> R.string.validation_latitude_range
            else -> null
        }

        val lng = current.longitude.toDoubleOrNull()
        val longitudeError = when {
            current.longitude.isBlank() -> R.string.validation_longitude_required
            lng == null -> R.string.validation_longitude_number
            lng < -180.0 || lng > 180.0 -> R.string.validation_longitude_range
            else -> null
        }

        _state.update {
            it.copy(
                titleError = titleError,
                descriptionError = descriptionError,
                startTimeError = startTimeError,
                latitudeError = latitudeError,
                longitudeError = longitudeError,
            )
        }

        val allValid = listOf(
            titleError, descriptionError, startTimeError, latitudeError, longitudeError,
        ).all { it == null }

        return allValid && validateEditLimits()
    }

    /**
     * F-12 - the limits that only apply when changing an existing event.
     *
     * Run after the ordinary checks, so a blank title is reported before a
     * scheduling complaint about a time that was never valid anyway.
     */
    private fun validateEditLimits(): Boolean {
        val before = original ?: return true
        val form = _state.value
        val newStart = form.startTime ?: return true

        if (EventEditRules.hasStarted(before)) {
            _state.update { it.copy(startTimeError = R.string.edit_error_already_started) }
            return false
        }

        if (EventEditRules.exceedsRescheduleLimit(before, newStart)) {
            _state.update { it.copy(startTimeError = R.string.edit_error_too_far) }
            return false
        }

        if (EventEditRules.isForbiddenEarlyMove(before, newStart)) {
            _state.update { it.copy(startTimeError = R.string.edit_error_no_earlier) }
            return false
        }

        val lat = form.latitude.toDoubleOrNull()
        val lng = form.longitude.toDoubleOrNull()
        if (lat != null && lng != null &&
            EventEditRules.exceedsRelocationLimit(before, lat, lng)
        ) {
            _state.update { it.copy(latitudeError = R.string.edit_error_too_far_away) }
            return false
        }

        if (form.capacity.isNotBlank() && (form.capacity.toIntOrNull() ?: 0) < 1) {
            _state.update { it.copy(titleError = R.string.edit_error_capacity) }
            return false
        }

        return true
    }

    fun save() {
        if (!validate()) return

        val form = _state.value
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val before = original

            // The access code is generated once and never regenerated: it has
            // already been shared, and a new one would lock people out. The same
            // reasoning applies to visibility, which is why the form does not
            // offer it while editing.
            val accessCode = when {
                before != null -> before.accessCode
                form.visibility == Visibility.PRIVATE -> generateAccessCode()
                else -> null
            }

            val event = Event(
                id = before?.id ?: UUID.randomUUID().toString(),
                ownerId = before?.ownerId ?: currentUser.id,
                title = form.title.trim(),
                description = form.description.trim(),
                latitude = form.latitude.toDouble(),
                longitude = form.longitude.toDouble(),
                startTime = form.startTime!!,
                category = form.category,
                // Immutable once created - see accessCode above.
                visibility = before?.visibility ?: form.visibility,
                imageUris = form.imageUris,
                address = form.address.trim().ifBlank { null },
                capacity = form.capacity.toIntOrNull(),
                price = form.price.toDoubleOrNull(),
                requiresReservation = form.requiresReservation,
                accessCode = accessCode,
            )

            if (before != null) {
                // Editing: one call, which updates locally and publishes. The
                // server re-checks every limit and its answer wins.
                applicationScope.launch { repository.updateEvent(event) }
            } else {
                // Local first. The event is the user's the moment they press
                // save, whether or not a server is reachable.
                repository.saveEvent(event)

                // F-15 - then upload, without making the user wait. Offline this
                // would otherwise block the screen for the whole connect
                // timeout; the row keeps syncedToBackend = false instead.
                //
                // Private events are uploaded too: an access code is only useful
                // if the server can resolve it (F-21).
                applicationScope.launch { repository.pushEvent(event) }
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    isSaved = true,
                    // Only worth announcing when it was just generated.
                    savedAccessCode = if (before == null) accessCode else null,
                )
            }
        }
    }

    private fun generateAccessCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
