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
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.domain.model.Visibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
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

    // Popunjava validate(); null = polje je ispravno
    @StringRes val titleError: Int? = null,
    @StringRes val descriptionError: Int? = null,
    @StringRes val startTimeError: Int? = null,
    @StringRes val latitudeError: Int? = null,
    @StringRes val longitudeError: Int? = null,

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    /** F-31: zahtev u toku, dugme je iskljuceno */
    val isSuggesting: Boolean = false,
    @StringRes val aiSuggestError: Int? = null,
    val savedAccessCode: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val currentUser: CurrentUser,
    private val locationProvider: LocationProvider,
    /** Application scope, da upload ne pukne kad se ekran zatvori */
    private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** null kod pravljenja, id dogadjaja kod izmene */
    private val editingId: String? = savedStateHandle[OrbitDestinations.EVENT_ID_ARG]

    /** Dogadjaj pre izmene, ogranicenja se racunaju od njega */
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
    /** F-17: rezultat sa mape; Locale.ROOT zbog decimalnog zareza */
    fun onLocationPicked(latitude: Double, longitude: Double) = _state.update {
        it.copy(
            latitude = String.format(Locale.ROOT, "%.6f", latitude),
            longitude = String.format(Locale.ROOT, "%.6f", longitude),
            latitudeError = null,
            longitudeError = null,
        )
    }

    /** F-17: pocetna lokacija za mapu; null bez dozvole */
    suspend fun deviceLocation(): UserLocation? = locationProvider.currentLocation()

    /** F-31: popunjava kategoriju i opis iz AI predloga */
    fun suggestWithAi() {
        val current = _state.value
        if (current.isSuggesting) return
        if (current.title.isBlank()) {
            _state.update { it.copy(titleError = R.string.validation_title_required) }
            return
        }

        _state.update { it.copy(isSuggesting = true, aiSuggestError = null) }
        viewModelScope.launch {
            val suggestion = repository.suggestEventDetails(current.title, current.description)
            _state.update {
                if (suggestion == null) {
                    it.copy(isSuggesting = false, aiSuggestError = R.string.create_ai_suggest_failed)
                } else {
                    it.copy(
                        isSuggesting = false,
                        category = suggestion.category,
                        description = suggestion.description,
                        descriptionError = null,
                    )
                }
            }
        }
    }

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

    /** F-12: ogranicenja koja vaze samo za izmenu */
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

            // Kod se generise samo jednom, vec je podeljen
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
                // Ne menja se posle kreiranja, kao accessCode
                visibility = before?.visibility ?: form.visibility,
                imageUris = form.imageUris,
                address = form.address.trim().ifBlank { null },
                capacity = form.capacity.toIntOrNull(),
                price = form.price.toDoubleOrNull(),
                requiresReservation = form.requiresReservation,
                accessCode = accessCode,
            )

            if (before != null) {
                // Izmena: jedan poziv, server ponovo proverava ogranicenja
                applicationScope.launch { repository.updateEvent(event) }
            } else {
                // Prvo lokalno, dogadjaj je sacuvan i bez servera
                repository.saveEvent(event)

                // F-15: upload u pozadini, i privatni zbog koda (F-21)
                applicationScope.launch { repository.pushEvent(event) }
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    isSaved = true,
                    // Kod prikazujemo samo kad je upravo generisan
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
