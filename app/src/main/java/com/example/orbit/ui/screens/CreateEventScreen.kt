package com.example.orbit.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.components.CategoryChipRow
import com.example.orbit.ui.components.DateTimePickerField
import com.example.orbit.ui.stateholders.CreateEventViewModel
import com.example.orbit.ui.stateholders.EventDetailViewModel

/**
 * F-08 / F-10 / F-20 - the create-event form.
 *
 * The screen holds no data of its own. It reads state from the ViewModel and reports
 * every keystroke back with an on...Change call. That one-way flow is why rotating the
 * phone does not wipe the form.
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

    // F-08 - the Android Photo Picker. It needs NO storage permission at all: the system
    // shows its own picker and hands back only what the user chose.
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
    ) { uris ->
        uris.forEach { uri ->
            // Without this the URI works right now but is dead after an app restart.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.onImagesPicked(uris.map { it.toString() })
    }

    // Once saved: public events just close. Private ones first show their access code.
    LaunchedEffect(state.isSaved, state.savedAccessCode) {
        if (state.isSaved && state.savedAccessCode == null) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New event") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                },
            )
        },
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                isError = state.titleError != null,
                supportingText = { state.titleError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                isError = state.descriptionError != null,
                supportingText = { state.descriptionError?.let { Text(it) } },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            CategoryChipRow(
                selected = state.category,
                onSelect = viewModel::onCategoryChange,
            )

            HorizontalDivider()

            Text("When", style = MaterialTheme.typography.titleSmall)
            DateTimePickerField(
                value = state.startTime,
                onValueChange = viewModel::onStartTimeChange,
                errorMessage = state.startTimeError,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("Where", style = MaterialTheme.typography.titleSmall)
            // TODO(F-17): prefill these from the device location instead of typing them.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.latitude,
                    onValueChange = viewModel::onLatitudeChange,
                    label = { Text("Latitude") },
                    isError = state.latitudeError != null,
                    supportingText = { state.latitudeError?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.longitude,
                    onValueChange = viewModel::onLongitudeChange,
                    label = { Text("Longitude") },
                    isError = state.longitudeError != null,
                    supportingText = { state.longitudeError?.let { Text(it) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Address (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("Photos", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) { Text("Add photos") }

            if (state.imageUris.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.imageUris.size) { index ->
                        val uri = state.imageUris[index]
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            TextButton(onClick = { viewModel.onImageRemoved(uri) }) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Details (optional)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.capacity,
                    onValueChange = viewModel::onCapacityChange,
                    label = { Text("Capacity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.price,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Price") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Requires reservation")
                Switch(
                    checked = state.requiresReservation,
                    onCheckedChange = viewModel::onRequiresReservationChange,
                )
            }

            HorizontalDivider()

            Text("Visibility", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Visibility.entries.forEach { option ->
                    FilterChip(
                        selected = state.visibility == option,
                        onClick = { viewModel.onVisibilityChange(option) },
                        label = {
                            Text(if (option == Visibility.PUBLIC) "Public" else "Private")
                        },
                    )
                }
            }
            if (state.visibility == Visibility.PRIVATE) {
                Text(
                    "A 6-character access code will be generated so you can share this event.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Saving..." else "Save event")
            }
        }
    }

    // F-20 - show the generated code before leaving the screen.
    val accessCode = state.savedAccessCode
    if (state.isSaved && accessCode != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Private event created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share this code so others can join:")
                    Text(accessCode, style = MaterialTheme.typography.headlineMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = onSaved) { Text("Done") }
            },
        )
    }
}
