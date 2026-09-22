package com.example.orbit.ui.stateholders

import com.example.orbit.ui.navigation.OrbitDestinations

import com.example.orbit.domain.model.MAX_EVENT_PHOTOS
import com.example.orbit.domain.model.EventEditRules

import androidx.lifecycle.SavedStateHandle

import kotlinx.coroutines.CoroutineScope

import com.example.orbit.R

import androidx.annotation.StringRes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.location.LocationProvider
import com.example.orbit.data.repository.EditResult
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventDuration
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
    /** F-35: oba prazna znaci da trajanje nije zadato */
    val durationHours: String = "",
    val durationMinutes: String = "",
    val imageUris: List<String> = emptyList(),

    // Popunjava validate(); null = polje je ispravno
    @StringRes val titleError: Int? = null,
    @StringRes val descriptionError: Int? = null,
    @StringRes val startTimeError: Int? = null,
    @StringRes val latitudeError: Int? = null,
    @StringRes val longitudeError: Int? = null,
    @StringRes val capacityError: Int? = null,
    @StringRes val priceError: Int? = null,
    @StringRes val durationError: Int? = null,
    /** Bez fotografije se dogadjaj ne cuva; kartica i detalj izgledaju prazno bez nje */
    @StringRes val imagesError: Int? = null,

    /** Korak koji je trenutno na ekranu */
    val step: FormStep = FormStep.BASICS,

    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    /** F-31: zahtev u toku, dugme je iskljuceno */
    val isSuggesting: Boolean = false,
    @StringRes val aiSuggestError: Int? = null,
    val savedAccessCode: String? = null,
    val isSaved: Boolean = false,

    /** F-12: izmena koju server nije prihvatio; forma ostaje otvorena */
    @StringRes val saveError: Int? = null,
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
            val duration = event.durationMinutes?.let { EventDuration.split(it) }
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
                price = event.price?.let(::formatPriceInput).orEmpty(),
                durationHours = duration?.first?.toString().orEmpty(),
                durationMinutes = duration?.second?.toString().orEmpty(),
                imageUris = event.imageUris,
                isEditing = true,
            )
        }
    }

    /**
     * Cela cena bez decimala, da polje ne pokazuje `800.0`.
     * Locale.ROOT jer se sadrzaj posle cita sa `toDoubleOrNull`, koji trazi tacku.
     */
    private fun formatPriceInput(price: Double): String =
        if (price % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", price) else price.toString()

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
    fun onCapacityChange(value: String) =
        _state.update { it.copy(capacity = value, capacityError = null) }
    fun onPriceChange(value: String) = _state.update { it.copy(price = value, priceError = null) }
    fun onDurationHoursChange(value: String) =
        _state.update { it.copy(durationHours = value, durationError = null) }
    fun onDurationMinutesChange(value: String) =
        _state.update { it.copy(durationMinutes = value, durationError = null) }
    // Birac pusta do pet odjednom, ali vise biranja moze da premasi granicu, pa se sece ovde
    fun onImagesPicked(uris: List<String>) =
        _state.update {
            it.copy(
                imageUris = (it.imageUris + uris).distinct().take(MAX_EVENT_PHOTOS),
                imagesError = null,
            )
        }
    fun onImageRemoved(uri: String) =
        _state.update { it.copy(imageUris = it.imageUris - uri) }

    // ---- provera po koracima ----
    // Svaki korak proverava samo svoja polja, da greske sa koraka koji jos nije
    // vidjen ne osvanu unapred. Ogranicenja izmene (F-12) idu uz polje na koje se
    // odnose, pa se javljaju odmah, a ne tek pri cuvanju.

    private fun errorsFor(step: FormStep, form: CreateEventFormState): CreateEventFormState =
        when (step) {
            FormStep.BASICS -> basicsErrors(form)
            FormStep.WHEN_WHERE -> whenWhereErrors(form)
            FormStep.DETAILS -> detailsErrors(form)
        }

    private fun CreateEventFormState.hasErrorOn(step: FormStep): Boolean = when (step) {
        FormStep.BASICS -> titleError != null || descriptionError != null
        FormStep.WHEN_WHERE -> startTimeError != null || durationError != null ||
            latitudeError != null || longitudeError != null

        FormStep.DETAILS -> capacityError != null || priceError != null || imagesError != null
    }

    private fun basicsErrors(form: CreateEventFormState) = form.copy(
        titleError = if (form.title.isBlank()) R.string.validation_title_required else null,
        descriptionError =
            if (form.description.isBlank()) R.string.validation_description_required else null,
    )

    private fun whenWhereErrors(form: CreateEventFormState): CreateEventFormState {
        val startTimeError = when {
            form.startTime == null -> R.string.validation_start_time_required
            form.startTime <= System.currentTimeMillis() -> R.string.validation_start_time_future
            else -> editedStartTimeError(form.startTime)
        }

        val latitude = form.latitude.toDoubleOrNull()
        val longitude = form.longitude.toDoubleOrNull()

        val latitudeError = when {
            form.latitude.isBlank() -> R.string.validation_latitude_required
            latitude == null -> R.string.validation_latitude_number
            latitude < -90.0 || latitude > 90.0 -> R.string.validation_latitude_range
            // Udaljenost ima smisla tek kad su obe koordinate citljive
            longitude != null -> editedLocationError(latitude, longitude)
            else -> null
        }

        val longitudeError = when {
            form.longitude.isBlank() -> R.string.validation_longitude_required
            longitude == null -> R.string.validation_longitude_number
            longitude < -180.0 || longitude > 180.0 -> R.string.validation_longitude_range
            else -> null
        }

        val durationError = when (EventDuration.parse(form.durationHours, form.durationMinutes)) {
            EventDuration.Parsed.Invalid -> R.string.validation_duration_invalid
            EventDuration.Parsed.TooLong -> R.string.validation_duration_too_long
            else -> null
        }

        return form.copy(
            startTimeError = startTimeError,
            latitudeError = latitudeError,
            longitudeError = longitudeError,
            durationError = durationError,
        )
    }

    private fun detailsErrors(form: CreateEventFormState): CreateEventFormState {
        // Prazan kapacitet znaci neogranicen broj mesta, isto kao na serveru
        val capacity = form.capacity.trim()
        val capacityError = when {
            capacity.isEmpty() -> null
            (capacity.toIntOrNull() ?: 0) < 1 -> R.string.edit_error_capacity
            else -> editedCapacityError(capacity.toIntOrNull())
        }

        val price = form.price.trim()
        val priceError =
            if (price.isNotEmpty() && (price.toDoubleOrNull() ?: -1.0) < 0.0) {
                R.string.validation_price_invalid
            } else {
                null
            }

        val imagesError = if (form.imageUris.isEmpty()) R.string.validation_photo_required else null

        return form.copy(capacityError = capacityError, priceError = priceError, imagesError = imagesError)
    }

    // ---- F-12: ogranicenja koja vaze samo za izmenu ----

    @StringRes
    private fun editedStartTimeError(newStartTime: Long): Int? {
        val before = original ?: return null
        return when {
            EventEditRules.hasStarted(before) -> R.string.edit_error_already_started
            EventEditRules.exceedsRescheduleLimit(before, newStartTime) -> R.string.edit_error_too_far
            EventEditRules.isForbiddenEarlyMove(before, newStartTime) -> R.string.edit_error_no_earlier
            else -> null
        }
    }

    @StringRes
    private fun editedLocationError(latitude: Double, longitude: Double): Int? {
        val before = original ?: return null
        return if (EventEditRules.exceedsRelocationLimit(before, latitude, longitude)) {
            R.string.edit_error_too_far_away
        } else {
            null
        }
    }

    @StringRes
    private fun editedCapacityError(capacity: Int?): Int? {
        val before = original ?: return null
        return if (EventEditRules.isBelowRegistered(before, capacity)) {
            R.string.edit_error_capacity_below_registered
        } else {
            null
        }
    }

    // ---- kretanje kroz korake ----

    /** Dalje tek kad tekuci korak prodje; neispravan korak ostaje na ekranu sa greskama */
    fun onNextStep() {
        val current = _state.value.step
        val validated = errorsFor(current, _state.value)
        _state.value = validated
        if (validated.hasErrorOn(current)) return

        FormStep.entries.getOrNull(current.ordinal + 1)?.let { next ->
            _state.update { it.copy(step = next) }
        }
    }

    fun onPreviousStep() {
        FormStep.entries.getOrNull(_state.value.step.ordinal - 1)?.let { previous ->
            _state.update { it.copy(step = previous) }
        }
    }

    /**
     * Skok na izabran korak. Radi samo kod izmene: tamo se ispravlja nesto odredjeno,
     * dok pravljenje novog dogadjaja ide redom.
     */
    fun onStepSelected(step: FormStep) {
        if (!_state.value.isEditing) return
        _state.update { it.copy(step = step) }
    }

    fun save() {
        // Izmena sme da skoci pravo na poslednji korak, pa se pri cuvanju
        // proveravaju svi; ekran zatim skace na prvi koji ne prolazi
        var validated = _state.value
        FormStep.entries.forEach { validated = errorsFor(it, validated) }

        val firstInvalid = FormStep.entries.firstOrNull { validated.hasErrorOn(it) }
        if (firstInvalid != null) {
            _state.value = validated.copy(step = firstInvalid)
            return
        }
        _state.value = validated

        val form = _state.value
        _state.update { it.copy(isSaving = true, saveError = null) }

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
                capacity = form.capacity.trim().toIntOrNull(),
                price = form.price.trim().toDoubleOrNull(),
                // Bez ovoga izmena je slala null i brisala trajanje na serveru
                durationMinutes = (EventDuration.parse(form.durationHours, form.durationMinutes)
                    as? EventDuration.Parsed.Valid)?.minutes,
                // Broj prijava i ocene menja samo server; lokalna kopija ih zadrzava i bez mreze
                registeredCount = before?.registeredCount ?: 0,
                avgRating = before?.avgRating ?: 0f,
                ratingCount = before?.ratingCount ?: 0,
                createdAt = before?.createdAt ?: System.currentTimeMillis(),
                accessCode = accessCode,
            )

            if (before != null) {
                // Izmena ceka odgovor servera; forma se zatvara tek kad je izmena zaista sacuvana
                val error = when (repository.updateEvent(event)) {
                    EditResult.Success -> null
                    EditResult.NoConnection -> R.string.error_action_offline
                    EditResult.Rejected -> R.string.edit_error_rejected
                }
                if (error != null) {
                    _state.update { it.copy(isSaving = false, saveError = error) }
                    return@launch
                }
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
