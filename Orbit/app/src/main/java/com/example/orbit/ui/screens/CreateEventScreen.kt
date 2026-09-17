package com.example.orbit.ui.screens

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.camera.CameraPermissionRequester
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.EventDuration
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.components.CameraCaptureView
import com.example.orbit.ui.components.CategoryChipRow
import com.example.orbit.ui.components.DateTimePickerField
import com.example.orbit.ui.components.FormStepIndicator
import com.example.orbit.ui.components.LocationPickerView
import com.example.orbit.ui.stateholders.CreateEventFormState
import com.example.orbit.ui.stateholders.CreateEventViewModel
import com.example.orbit.ui.stateholders.FormStep
import org.osmdroid.util.GeoPoint

/** Razmak izmedju polja unutar jedne grupe */
private val FIELD_GAP = 16.dp

/**
 * Dodatak na razmak izmedju grupa. `spacedBy(FIELD_GAP)` vec dodaje 16 sa obe strane,
 * pa razdvajanje grupa ispadne 40 dp - vise nego dvostruko u odnosu na razmak unutar grupe.
 */
private val EXTRA_GROUP_GAP = 8.dp

/**
 * F-08/F-10/F-20: forma za pravljenje i izmenu dogadjaja, podeljena na tri koraka.
 * Pravljenje ide redom, izmena skace pravo na korak koji se ispravlja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel<CreateEventViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraDeniedMessage = stringResource(R.string.camera_permission_denied)
    val cameraUnavailableMessage = stringResource(R.string.camera_unavailable)

    // F-08: Photo Picker, ne treba dozvola za skladiste
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        uris.forEach { uri ->
            // Bez ovoga URI ne radi posle restarta
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.onImagesPicked(uris.map { it.toString() })
    }

    // F-08: kamera preko forme, da se ne izgubi stanje
    var showCamera by rememberSaveable { mutableStateOf(false) }

    // F-17: izbor na mapi, preko forme kao kamera
    var showLocationPicker by rememberSaveable { mutableStateOf(false) }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Launcher se registruje u kompoziciji, ne u trenutku pitanja
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(context, cameraDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }

    // Javni se odmah zatvara, privatni prvo prikaze kod
    LaunchedEffect(state.isSaved, state.savedAccessCode) {
        if (state.isSaved && state.savedAccessCode == null) onSaved()
    }

    val stepLabels = FormStep.entries.map { stringResource(it.labelRes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.edit_title
                            else R.string.create_title
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        },
        bottomBar = {
            StepBar(
                state = state,
                onBack = viewModel::onPreviousStep,
                onNext = viewModel::onNextStep,
                onSave = viewModel::save,
            )
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {

            FormStepIndicator(
                stepLabels = stepLabels,
                currentIndex = state.step.ordinal,
                // Kod izmene se ispravlja nesto odredjeno, pa se skace pravo na korak
                onStepSelected = if (state.isEditing) {
                    { index -> FormStep.entries.getOrNull(index)?.let(viewModel::onStepSelected) }
                } else {
                    null
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
            ) {
                when (state.step) {
                    FormStep.BASICS -> BasicsStep(state, viewModel)

                    FormStep.WHEN_WHERE -> WhenWhereStep(
                        state = state,
                        viewModel = viewModel,
                        onPickOnMap = { showLocationPicker = true },
                    )

                    FormStep.DETAILS -> DetailsStep(
                        state = state,
                        viewModel = viewModel,
                        onAddPhotos = {
                            pickImages.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onTakePhoto = {
                            if (CameraPermissionRequester.hasPermissions(context)) {
                                showCamera = true
                            } else {
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCamera) {
        CameraCaptureView(
            onPhotoTaken = { uri ->
                viewModel.onImagesPicked(listOf(uri.toString()))
                showCamera = false
            },
            onCancel = { showCamera = false },
            onUnavailable = {
                Toast.makeText(context, cameraUnavailableMessage, Toast.LENGTH_LONG).show()
                showCamera = false
            },
        )
        // Dok je kamera otvorena, ostatak se ne crta
        return
    }

    if (showLocationPicker) {
        val lat = state.latitude.toDoubleOrNull()
        val lng = state.longitude.toDoubleOrNull()
        LocationPickerView(
            initial = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            deviceLocation = viewModel::deviceLocation,
            onConfirm = { latitude, longitude ->
                viewModel.onLocationPicked(latitude, longitude)
                showLocationPicker = false
            },
            onCancel = { showLocationPicker = false },
        )
        return
    }

    // F-20: prikazi kod pre izlaska sa ekrana
    val accessCode = state.savedAccessCode
    if (state.isSaved && accessCode != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.create_code_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.create_code_dialog_text))
                    Text(accessCode, style = MaterialTheme.typography.headlineMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = onSaved) { Text(stringResource(R.string.common_done)) }
            },
        )
    }
}

/**
 * Kretanje kroz korake, u zoni palca.
 * Kod izmene nema Next: koraci se biraju indikatorom, a cuvanje je dostupno sa svakog.
 */
@Composable
private fun StepBar(
    state: CreateEventFormState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    val isLastStep = state.step == FormStep.entries.last()

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isEditing && state.step != FormStep.entries.first()) {
                    OutlinedButton(onClick = onBack) {
                        Text(stringResource(R.string.create_step_back))
                    }
                }

                val savingNow = state.isSaving
                val savesHere = state.isEditing || isLastStep

                Button(
                    onClick = if (savesHere) onSave else onNext,
                    enabled = !savingNow,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(
                            when {
                                savingNow -> R.string.create_saving
                                state.isEditing -> R.string.edit_save
                                isLastStep -> R.string.create_save
                                else -> R.string.create_step_next
                            }
                        )
                    )
                }
            }
        }
    }
}

/** Korak 1: naslov, opis, AI predlog i kategorija */
@Composable
private fun BasicsStep(
    state: CreateEventFormState,
    viewModel: CreateEventViewModel,
) {
    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::onTitleChange,
        label = { Text(stringResource(R.string.create_field_title)) },
        isError = state.titleError != null,
        supportingText = { state.titleError?.let { Text(stringResource(it)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = state.description,
        onValueChange = viewModel::onDescriptionChange,
        label = { Text(stringResource(R.string.create_field_description)) },
        isError = state.descriptionError != null,
        supportingText = { state.descriptionError?.let { Text(stringResource(it)) } },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    // F-31: predlog stoji iznad kategorije jer je i popunjava, pa se rezultat vidi odmah ispod
    OutlinedButton(
        onClick = viewModel::suggestWithAi,
        enabled = !state.isSuggesting,
    ) {
        Text(
            stringResource(
                if (state.isSuggesting) R.string.create_ai_suggesting
                else R.string.create_ai_suggest
            )
        )
    }

    Text(
        text = state.aiSuggestError?.let { stringResource(it) }
            ?: stringResource(R.string.create_ai_suggest_hint),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.aiSuggestError != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )

    Spacer(modifier = Modifier.height(EXTRA_GROUP_GAP))

    Text(
        text = stringResource(R.string.create_section_category),
        style = MaterialTheme.typography.titleSmall,
    )
    CategoryChipRow(
        selected = state.category,
        onSelect = viewModel::onCategoryChange,
    )
}

/** Korak 2: termin, trajanje i mesto */
@Composable
private fun WhenWhereStep(
    state: CreateEventFormState,
    viewModel: CreateEventViewModel,
    onPickOnMap: () -> Unit,
) {
    Text(
        text = stringResource(R.string.create_section_when),
        style = MaterialTheme.typography.titleSmall,
    )

    DateTimePickerField(
        value = state.startTime,
        onValueChange = viewModel::onStartTimeChange,
        errorMessage = state.startTimeError,
        modifier = Modifier.fillMaxWidth(),
    )

    // F-35: trajanje odredjuje do kada se potvrdjuje dolazak
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.durationHours,
            onValueChange = viewModel::onDurationHoursChange,
            label = { Text(stringResource(R.string.create_field_duration_hours)) },
            singleLine = true,
            isError = state.durationError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.durationMinutes,
            onValueChange = viewModel::onDurationMinutesChange,
            label = { Text(stringResource(R.string.create_field_duration_minutes)) },
            singleLine = true,
            isError = state.durationError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = state.durationError?.let { stringResource(it, EventDuration.MAX_DAYS) }
            ?: stringResource(
                R.string.create_duration_hint,
                AttendanceRules.DEFAULT_DURATION_MINUTES / 60,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.durationError != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )

    Spacer(modifier = Modifier.height(EXTRA_GROUP_GAP))

    Text(
        text = stringResource(R.string.create_section_where),
        style = MaterialTheme.typography.titleSmall,
    )
    OutlinedButton(onClick = onPickOnMap) {
        Text(stringResource(R.string.create_pick_on_map))
    }

    // I dalje moze rucni unos koordinata
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.latitude,
            onValueChange = viewModel::onLatitudeChange,
            label = { Text(stringResource(R.string.create_field_latitude)) },
            isError = state.latitudeError != null,
            supportingText = { state.latitudeError?.let { Text(stringResource(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.longitude,
            onValueChange = viewModel::onLongitudeChange,
            label = { Text(stringResource(R.string.create_field_longitude)) },
            isError = state.longitudeError != null,
            supportingText = { state.longitudeError?.let { Text(stringResource(it)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }

    OutlinedTextField(
        value = state.address,
        onValueChange = viewModel::onAddressChange,
        label = { Text(stringResource(R.string.create_field_address)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Korak 3: fotografije, kapacitet, cena i vidljivost */
@Composable
private fun DetailsStep(
    state: CreateEventFormState,
    viewModel: CreateEventViewModel,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    Text(
        text = stringResource(R.string.create_section_photos),
        style = MaterialTheme.typography.titleSmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onAddPhotos) {
            Text(stringResource(R.string.create_add_photos))
        }
        OutlinedButton(onClick = onTakePhoto) {
            Text(stringResource(R.string.create_take_photo))
        }
    }

    if (state.imageUris.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.imageUris.size) { index ->
                val uri = state.imageUris[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageUrls.model(uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    TextButton(onClick = { viewModel.onImageRemoved(uri) }) {
                        Text(stringResource(R.string.create_remove_photo))
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(EXTRA_GROUP_GAP))

    Text(
        text = stringResource(R.string.create_section_details),
        style = MaterialTheme.typography.titleSmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Kapacitet ogranicava prijave, prazno je bez ogranicenja
        OutlinedTextField(
            value = state.capacity,
            onValueChange = viewModel::onCapacityChange,
            label = { Text(stringResource(R.string.create_field_capacity)) },
            singleLine = true,
            isError = state.capacityError != null,
            supportingText = {
                Text(stringResource(state.capacityError ?: R.string.create_capacity_hint))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.price,
            onValueChange = viewModel::onPriceChange,
            label = { Text(stringResource(R.string.create_field_price)) },
            singleLine = true,
            isError = state.priceError != null,
            supportingText = { state.priceError?.let { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(EXTRA_GROUP_GAP))

    Text(
        text = stringResource(R.string.create_section_visibility),
        style = MaterialTheme.typography.titleSmall,
    )

    // F-12: vidljivost se ne menja posle kreiranja; kod izmene ostaje kao podatak,
    // da korak ne izgleda kao da mu fali polje
    if (state.isEditing) {
        Text(
            text = stringResource(state.visibility.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.create_visibility_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Visibility.entries.forEach { option ->
                FilterChip(
                    selected = state.visibility == option,
                    onClick = { viewModel.onVisibilityChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        if (state.visibility == Visibility.PRIVATE) {
            Text(
                text = stringResource(R.string.create_private_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
