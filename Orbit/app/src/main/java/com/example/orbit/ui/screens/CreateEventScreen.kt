package com.example.orbit.ui.screens

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.data.camera.CameraPermissionRequester
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.EventDuration
import com.example.orbit.domain.model.MAX_EVENT_PHOTOS
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.components.AiSuggestCard
import com.example.orbit.ui.components.ButtonIconLabel
import com.example.orbit.ui.components.CameraCaptureView
import com.example.orbit.ui.components.CategoryPicker
import com.example.orbit.ui.components.CountPill
import com.example.orbit.ui.components.DateTimePickerField
import com.example.orbit.ui.components.FormSection
import com.example.orbit.ui.components.FormStepIndicator
import com.example.orbit.ui.components.LocationPickerView
import com.example.orbit.ui.components.OrbitFormTopBar
import com.example.orbit.ui.components.PhotoStrip
import com.example.orbit.ui.components.VisibilityOption
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.stateholders.CreateEventFormState
import com.example.orbit.ui.stateholders.CreateEventViewModel
import com.example.orbit.ui.stateholders.FormStep
import com.example.orbit.ui.theme.orbitAccents
import kotlinx.coroutines.launch

/** Razmak izmedju kartica forme */
private val SECTION_GAP = 16.dp

/** Brzi izbori trajanja u satima; ostalo se kuca u polja ispod */
private val DURATION_PRESETS = listOf(1, 2, 3)

/** Podloga izabranog cipa trajanja; tekst ostaje u boji teksta */
private const val CHIP_FILL_ALPHA = 0.15f

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
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_EVENT_PHOTOS),
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

    // Brend zelena, kao zaglavlje naloga; pocetna kategorija Ostalo je smedja,
    // pa bi forma sa njenom bojom izgledala bledo dok se kategorija ne izabere
    val accent = MaterialTheme.orbitAccents.brandStart

    Scaffold(
        topBar = {
            OrbitFormTopBar(
                onCancel = onCancel,
                title = stringResource(
                    if (state.isEditing) R.string.edit_title
                    else R.string.create_title
                ),
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

            // Korak ulazi sa strane na koju se ide; svaki korak ima svoj skrol
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { stepTransition(forward = targetState.ordinal > initialState.ordinal) },
                modifier = Modifier.weight(1f),
                label = "formStep",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                ) {
                    when (step) {
                        FormStep.BASICS -> BasicsStep(state = state, viewModel = viewModel, accent = accent)

                        FormStep.WHEN_WHERE -> WhenWhereStep(
                            state = state,
                            viewModel = viewModel,
                            accent = accent,
                            onPickOnMap = { showLocationPicker = true },
                        )

                        FormStep.DETAILS -> DetailsStep(
                            state = state,
                            viewModel = viewModel,
                            accent = accent,
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
            initialLatitude = lat,
            initialLongitude = lng,
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

/** Sledeci korak ulazi zdesna, prethodni sleva; stari bledi */
private fun stepTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (slideInHorizontally { width -> direction * width / 4 } + fadeIn()) togetherWith
        (slideOutHorizontally { width -> -direction * width / 4 } + fadeOut())
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

            // Uz dugme, jer se odnosi na cuvanje, a ne na jedno polje
            state.saveError?.let {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    HintText(text = stringResource(it), isError = true)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.isEditing && state.step != FormStep.entries.first()) {
                    OutlinedButton(
                        onClick = onBack,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        ButtonIconLabel(
                            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                            text = stringResource(R.string.create_step_back),
                        )
                    }
                }

                val savingNow = state.isSaving
                val savesHere = state.isEditing || isLastStep

                Button(
                    onClick = if (savesHere) onSave else onNext,
                    enabled = !savingNow,
                    modifier = Modifier.weight(1f),
                ) {
                    when {
                        savingNow -> {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.create_saving))
                        }

                        savesHere -> ButtonIconLabel(
                            icon = rememberVectorPainter(Icons.Filled.Check),
                            text = stringResource(if (state.isEditing) R.string.edit_save else R.string.create_save),
                        )

                        // Strelica posle teksta kaze da se ide napred
                        else -> ButtonIconLabel(
                            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward),
                            text = stringResource(R.string.create_step_next),
                            iconAfterText = true,
                        )
                    }
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
    accent: Color,
) {
    FormSection(
        icon = rememberVectorPainter(Icons.Filled.Edit),
        title = stringResource(R.string.create_section_title_description),
        accent = accent,
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text(stringResource(R.string.create_field_title)) },
            isError = state.titleError != null,
            supportingText = { state.titleError?.let { Text(stringResource(it)) } },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.create_field_description)) },
            isError = state.descriptionError != null,
            supportingText = { state.descriptionError?.let { Text(stringResource(it)) } },
            minLines = 3,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // F-31: predlog stoji iznad kategorije jer je i popunjava, pa se rezultat vidi odmah ispod
    AiSuggestCard(
        isSuggesting = state.isSuggesting,
        error = state.aiSuggestError,
        onSuggest = viewModel::suggestWithAi,
    )

    FormSection(
        icon = painterResource(state.category.iconRes()),
        title = stringResource(R.string.create_section_category),
        accent = accent,
    ) {
        CategoryPicker(
            selected = state.category,
            onSelect = viewModel::onCategoryChange,
        )
    }
}

/** Korak 2: termin, trajanje i mesto */
@Composable
private fun WhenWhereStep(
    state: CreateEventFormState,
    viewModel: CreateEventViewModel,
    accent: Color,
    onPickOnMap: () -> Unit,
) {
    FormSection(
        icon = rememberVectorPainter(Icons.Filled.DateRange),
        title = stringResource(R.string.create_section_when),
        accent = accent,
    ) {
        DateTimePickerField(
            value = state.startTime,
            onValueChange = viewModel::onStartTimeChange,
            accent = accent,
            errorMessage = state.startTimeError,
            modifier = Modifier.fillMaxWidth(),
        )

        // F-35: trajanje odredjuje do kada se potvrdjuje dolazak
        Text(
            text = stringResource(R.string.create_duration_label),
            style = MaterialTheme.typography.labelLarge,
        )

        // Cest izbor jednim dodirom; popunjava ista polja kao kucanje
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DURATION_PRESETS.forEach { hours ->
                val isSelected = state.durationHours == hours.toString() && state.durationMinutes.isBlank()
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.onDurationHoursChange(hours.toString())
                        viewModel.onDurationMinutesChange("")
                    },
                    label = { Text(stringResource(R.string.create_duration_preset, hours)) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = CHIP_FILL_ALPHA),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.durationHours,
                onValueChange = viewModel::onDurationHoursChange,
                label = { Text(stringResource(R.string.create_field_duration_hours)) },
                singleLine = true,
                isError = state.durationError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.durationMinutes,
                onValueChange = viewModel::onDurationMinutesChange,
                label = { Text(stringResource(R.string.create_field_duration_minutes)) },
                singleLine = true,
                isError = state.durationError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
        }

        HintText(
            text = state.durationError?.let { stringResource(it, EventDuration.MAX_DAYS) }
                ?: stringResource(
                    R.string.create_duration_hint,
                    AttendanceRules.DEFAULT_DURATION_MINUTES / 60,
                ),
            isError = state.durationError != null,
        )
    }

    FormSection(
        icon = rememberVectorPainter(Icons.Filled.Place),
        title = stringResource(R.string.create_section_where),
        accent = accent,
    ) {
        LocationStatus(state)

        val scope = rememberCoroutineScope()
        val useDeviceLocation = {
            scope.launch {
                viewModel.deviceLocation()?.let { viewModel.onLocationPicked(it.latitude, it.longitude) }
            }
            Unit
        }

        // Dozvola se trazi tek na klik; forma radi i bez nje, preko mape
        val locationPermission = rememberLocationPermissionState(
            onGranted = useDeviceLocation,
            askOnFirstAppearance = false,
        )

        val buttonColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = accent.copy(alpha = CHIP_FILL_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = onPickOnMap,
                colors = buttonColors,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.create_pick_on_map))
            }
            FilledTonalButton(
                onClick = {
                    if (locationPermission.granted) useDeviceLocation() else locationPermission.request()
                },
                colors = buttonColors,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.create_use_my_location))
            }
        }

        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::onAddressChange,
            label = { Text(stringResource(R.string.create_field_address)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Da li je lokacija izabrana; koordinate su sitno ispod, jer korisniku ne znace mnogo.
 * Koordinate se ne kucaju, ovde se samo vidi sta je izabrano.
 */
@Composable
private fun LocationStatus(state: CreateEventFormState) {
    val coordinatesError = state.latitudeError ?: state.longitudeError
    val hasCoordinates = state.latitude.isNotBlank() && state.longitude.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (hasCoordinates) Icons.Filled.CheckCircle else Icons.Filled.Place,
            contentDescription = null,
            tint = if (hasCoordinates) {
                MaterialTheme.orbitAccents.registered
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column {
            Text(
                text = stringResource(
                    if (hasCoordinates) R.string.create_location_set else R.string.create_location_none
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (hasCoordinates) {
                Text(
                    text = stringResource(R.string.create_location_picked, state.latitude, state.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    coordinatesError?.let { HintText(text = stringResource(it), isError = true) }
}

/** Korak 3: fotografije, kapacitet, cena i vidljivost */
@Composable
private fun DetailsStep(
    state: CreateEventFormState,
    viewModel: CreateEventViewModel,
    accent: Color,
    onAddPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    FormSection(
        icon = painterResource(R.drawable.ic_photo_camera),
        title = stringResource(R.string.create_section_photos),
        accent = accent,
        trailing = {
            CountPill(
                text = stringResource(R.string.create_photos_count, state.imageUris.size, MAX_EVENT_PHOTOS),
                accent = accent,
            )
        },
    ) {
        // Greska istim mestom kao pravilo, da se vidi sta nedostaje
        HintText(
            text = stringResource(state.imagesError ?: R.string.create_photos_hint, MAX_EVENT_PHOTOS),
            isError = state.imagesError != null,
        )

        PhotoStrip(
            uris = state.imageUris,
            // Na granici plocice za dodavanje nestaju; brojac kaze zasto
            canAddMore = state.imageUris.size < MAX_EVENT_PHOTOS,
            accent = accent,
            onRemove = viewModel::onImageRemoved,
            onAddFromGallery = onAddPhotos,
            onTakePhoto = onTakePhoto,
        )
    }

    FormSection(
        icon = rememberVectorPainter(Icons.Filled.Info),
        title = stringResource(R.string.create_section_details),
        accent = accent,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Kapacitet ogranicava prijave, prazno je bez ogranicenja
            OutlinedTextField(
                value = state.capacity,
                onValueChange = viewModel::onCapacityChange,
                label = { Text(stringResource(R.string.create_field_capacity)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                isError = state.capacityError != null,
                supportingText = {
                    Text(stringResource(state.capacityError ?: R.string.create_capacity_hint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.price,
                onValueChange = viewModel::onPriceChange,
                label = { Text(stringResource(R.string.create_field_price)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_price_tag), contentDescription = null) },
                suffix = { Text(stringResource(R.string.create_price_suffix)) },
                singleLine = true,
                isError = state.priceError != null,
                supportingText = {
                    Text(stringResource(state.priceError ?: R.string.create_price_hint))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
        }
    }

    FormSection(
        icon = painterResource(R.drawable.ic_public),
        title = stringResource(R.string.create_section_visibility),
        accent = accent,
    ) {
        // F-12: vidljivost se ne menja posle kreiranja; kod izmene ostaje kao podatak,
        // da korak ne izgleda kao da mu fali polje
        if (state.isEditing) {
            Text(
                text = stringResource(state.visibility.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
            )
            HintText(text = stringResource(R.string.create_visibility_locked))
        } else {
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VisibilityOption(
                    icon = painterResource(R.drawable.ic_public),
                    title = stringResource(Visibility.PUBLIC.labelRes()),
                    subtitle = stringResource(R.string.visibility_public_hint),
                    selected = state.visibility == Visibility.PUBLIC,
                    accent = accent,
                    onClick = { viewModel.onVisibilityChange(Visibility.PUBLIC) },
                    modifier = Modifier.weight(1f),
                )
                VisibilityOption(
                    icon = rememberVectorPainter(Icons.Filled.Lock),
                    title = stringResource(Visibility.PRIVATE.labelRes()),
                    subtitle = stringResource(R.string.visibility_private_hint),
                    selected = state.visibility == Visibility.PRIVATE,
                    accent = accent,
                    onClick = { viewModel.onVisibilityChange(Visibility.PRIVATE) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.visibility == Visibility.PRIVATE) {
                HintText(text = stringResource(R.string.create_private_hint))
            }
        }
    }
}

/** Sitan tekst ispod polja; greska je u boji greske, objasnjenje tise */
@Composable
private fun HintText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
