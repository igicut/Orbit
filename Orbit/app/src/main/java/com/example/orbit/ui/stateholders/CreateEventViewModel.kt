package com.example.orbit.ui.stateholders

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
    val titleError: String? = null,
    val descriptionError: String? = null,
    val startTimeError: String? = null,
    val latitudeError: String? = null,
    val longitudeError: String? = null,

    val isSaving: Boolean = false,
    val savedAccessCode: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val currentUser: CurrentUser,
) : ViewModel() {

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

        val titleError = if (current.title.isBlank()) "Title is required" else null
        val descriptionError =
            if (current.description.isBlank()) "Description is required" else null

        val startTimeError = when {
            current.startTime == null -> "Pick a start date and time"
            current.startTime <= System.currentTimeMillis() -> "Start time must be in the future"
            else -> null
        }

        val lat = current.latitude.toDoubleOrNull()
        val latitudeError = when {
            current.latitude.isBlank() -> "Latitude is required"
            lat == null -> "Latitude must be a number"
            lat < -90.0 || lat > 90.0 -> "Latitude must be between -90 and 90"
            else -> null
        }

        val lng = current.longitude.toDoubleOrNull()
        val longitudeError = when {
            current.longitude.isBlank() -> "Longitude is required"
            lng == null -> "Longitude must be a number"
            lng < -180.0 || lng > 180.0 -> "Longitude must be between -180 and 180"
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

        return listOf(
            titleError, descriptionError, startTimeError, latitudeError, longitudeError,
        ).all { it == null }
    }

    fun save() {
        if (!validate()) return

        val form = _state.value
        _state.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val accessCode =
                if (form.visibility == Visibility.PRIVATE) generateAccessCode() else null

            val event = Event(
                id = UUID.randomUUID().toString(),
                ownerId = currentUser.id,
                title = form.title.trim(),
                description = form.description.trim(),
                latitude = form.latitude.toDouble(),
                longitude = form.longitude.toDouble(),
                startTime = form.startTime!!,
                category = form.category,
                visibility = form.visibility,
                imageUris = form.imageUris,
                address = form.address.trim().ifBlank { null },
                capacity = form.capacity.toIntOrNull(),
                price = form.price.toDoubleOrNull(),
                requiresReservation = form.requiresReservation,
                accessCode = accessCode,
            )

            repository.saveEvent(event)

            _state.update {
                it.copy(isSaving = false, isSaved = true, savedAccessCode = accessCode)
            }
        }
    }

    private fun generateAccessCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
