package com.example.orbit.ui.screens

import com.example.orbit.domain.model.EventEditRules

import androidx.compose.material.icons.filled.Edit

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import com.example.orbit.ui.components.RatingBar

import com.example.orbit.ui.common.labelRes

import androidx.compose.ui.res.stringResource

import com.example.orbit.R

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.data.repository.CheckInResult
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.util.formatEventDate
import com.example.orbit.ui.util.formatEventDateTime
import com.example.orbit.ui.util.formatEventTime
import kotlinx.coroutines.delay

/** Prozori prijave i dolaska zavise od sata, pa se detalj sam osvezava */
private const val CLOCK_TICK_MS = 30_000L

/** F-09/F-19: detalji dogadjaja i navigacija */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel<EventDetailViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isRegistered by viewModel.isRegistered.collectAsStateWithLifecycle()
    val isRegistrationPending by viewModel.isRegistrationPending.collectAsStateWithLifecycle()
    val hasAttended by viewModel.hasAttended.collectAsStateWithLifecycle()
    val isCheckInPending by viewModel.isCheckInPending.collectAsStateWithLifecycle()
    val checkInProblem by viewModel.checkInProblem.collectAsStateWithLifecycle()
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    var showAttendees by remember { mutableStateOf(false) }
    val myRating by viewModel.myRating.collectAsStateWithLifecycle()
    val ratingError by viewModel.ratingError.collectAsStateWithLifecycle()
    val organiser by viewModel.organiser.collectAsStateWithLifecycle()
    val isOrganiserBlocked by viewModel.isOrganiserBlocked.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val isDeleted by viewModel.isDeleted.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Kratka poruka, stanje ikonice se nije promenilo
    val actionErrorMessage = actionError?.let { stringResource(it) }
    LaunchedEffect(actionErrorMessage) {
        actionErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearActionError()
        }
    }

    // Lokacija se trazi tek na klik za potvrdu, ne pri otvaranju detalja
    var checkInRequested by remember { mutableStateOf(false) }
    val locationPermission = rememberLocationPermissionState(
        askOnFirstAppearance = false,
        onDenied = {
            checkInRequested = false
            viewModel.onLocationPermissionDenied()
        },
    )
    LaunchedEffect(locationPermission.granted, checkInRequested) {
        if (checkInRequested && locationPermission.granted) {
            checkInRequested = false
            viewModel.checkIn()
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) {
            showDeleteDialog = false
            onBack()
        }
    }

    val event = (uiState as? UiState.Success)?.data
    val isOwner = event != null && event.ownerId == viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.title ?: "Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    // F-09: samo vlasnik; izmena i brisanje nestaju kad dogadjaj pocne,
                    // jer brisanje odnosi dolaske i ocene gostiju (server isto proverava)
                    if (isOwner && event != null &&
                        !EventEditRules.hasStarted(event)
                    ) {
                        IconButton(onClick = { onEdit(event.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.detail_edit),
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.clearDeleteError()
                                showDeleteDialog = true
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(Modifier.padding(innerPadding))
            is UiState.Error -> ErrorView(stringResource(state.messageRes), Modifier.padding(innerPadding))
            is UiState.Success -> {
                val loaded = state.data
                if (loaded == null) {
                    // Posle brisanja se odmah izlazi, bez poruke da nije pronadjen
                    if (!isDeleted) {
                        EmptyView(
                            title = stringResource(R.string.detail_not_found_title),
                            subtitle = stringResource(R.string.detail_not_found_subtitle),
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                } else {
                    EventDetailContent(
                        event = loaded,
                        myRating = myRating ?: 0,
                        isOwner = isOwner,
                        organiserName = organiser?.displayName,
                        isOrganiserBlocked = isOrganiserBlocked,
                        onToggleBlock = viewModel::toggleOrganiserBlocked,
                        isRegistered = isRegistered,
                        isRegistrationPending = isRegistrationPending,
                        onToggleRegistration = viewModel::toggleRegistration,
                        hasAttended = hasAttended,
                        isCheckInPending = isCheckInPending,
                        checkInProblem = checkInProblem,
                        onCheckIn = {
                            if (locationPermission.granted) {
                                viewModel.checkIn()
                            } else {
                                checkInRequested = true
                                locationPermission.request()
                            }
                        },
                        onShowAttendees = {
                            showAttendees = true
                            viewModel.loadAttendees()
                        },
                        ratingError = ratingError,
                        onRate = viewModel::submitRating,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.detail_delete_dialog_text))
                    deleteError?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    enabled = !isDeleting,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text(stringResource(R.string.detail_delete_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                ) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showAttendees) {
        AttendeesSheet(
            state = attendees,
            onRetry = viewModel::loadAttendees,
            onDismiss = { showAttendees = false },
        )
    }
}

@Composable
private fun EventDetailContent(
    event: Event,
    myRating: Int,
    ratingError: Int?,
    onRate: (Int) -> Unit,
    isOwner: Boolean,
    organiserName: String?,
    isOrganiserBlocked: Boolean,
    onToggleBlock: () -> Unit,
    isRegistered: Boolean,
    isRegistrationPending: Boolean,
    onToggleRegistration: () -> Unit,
    hasAttended: Boolean,
    isCheckInPending: Boolean,
    checkInProblem: CheckInResult?,
    onCheckIn: () -> Unit,
    onShowAttendees: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noMapsAppMessage = stringResource(R.string.detail_no_maps_app)

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        if (event.imageUris.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(event.imageUris.size) { index ->
                    AsyncImage(
                        model = event.imageUris[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        Text(event.title, style = MaterialTheme.typography.headlineSmall)
        AssistChip(onClick = { }, label = { Text(stringResource(event.category.labelRes())) })

        Text(formatEventDateTime(event.startTime), style = MaterialTheme.typography.bodyLarge)
        // F-35: kraj samo kad je trajanje zadato; datum samo ako se zavrsava drugog dana
        event.durationMinutes?.let {
            val endTime = AttendanceRules.endTime(event)
            val end = if (formatEventDate(endTime) == formatEventDate(event.startTime)) {
                formatEventTime(endTime)
            } else {
                formatEventDateTime(endTime)
            }
            Text(
                text = stringResource(R.string.detail_ends_at, end),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        event.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()
        Text(event.description, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()

        event.price?.let { Text(stringResource(R.string.detail_price, it.toString())) }
        event.accessCode?.let {
            Text(
                stringResource(R.string.detail_access_code, it),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            stringResource(
                R.string.detail_rating,
                event.avgRating.toString(),
                event.ratingCount,
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        RegistrationSection(
            event = event,
            now = now,
            isOwner = isOwner,
            isRegistered = isRegistered,
            isPending = isRegistrationPending,
            onToggle = onToggleRegistration,
            hasAttended = hasAttended,
            isCheckInPending = isCheckInPending,
            checkInProblem = checkInProblem,
            onCheckIn = onCheckIn,
            onShowAttendees = onShowAttendees,
        )

        // F-19: otvara maps aplikaciju preko geo: URI
        Button(
            onClick = {
                val uri = Uri.parse(
                    "geo:" + event.latitude + "," + event.longitude +
                        "?q=" + event.latitude + "," + event.longitude +
                        "(" + Uri.encode(event.title) + ")"
                )
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, noMapsAppMessage, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.detail_navigate)) }

        HorizontalDivider()

        // F-28: organizator i blokiranje, ne za svoje dogadjaje
        if (!isOwner) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.detail_organised_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Skraceni id ako profil nije preuzet
                        text = organiserName ?: event.ownerId.take(8),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                TextButton(onClick = onToggleBlock) {
                    Text(
                        stringResource(
                            if (isOrganiserBlocked) R.string.detail_unblock
                            else R.string.detail_block
                        )
                    )
                }
            }

            if (isOrganiserBlocked) {
                Text(
                    text = stringResource(R.string.detail_blocked_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()
        }

        // F-27: ocenjuju samo potvrdjeni dolasci, server isto proverava; organizator ne ocenjuje
        if (!isOwner) {
            Text(
                text = stringResource(R.string.rating_your_rating),
                style = MaterialTheme.typography.titleSmall,
            )

            if (hasAttended) {
                RatingBar(rating = myRating, onRatingChange = onRate)
                ratingError?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.rating_requires_attendance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Prijava i dolazak: broj mesta, jedno glavno dugme i stanje posle pocetka */
@Composable
private fun RegistrationSection(
    event: Event,
    now: Long,
    isOwner: Boolean,
    isRegistered: Boolean,
    isPending: Boolean,
    onToggle: () -> Unit,
    hasAttended: Boolean,
    isCheckInPending: Boolean,
    checkInProblem: CheckInResult?,
    onCheckIn: () -> Unit,
    onShowAttendees: () -> Unit,
) {
    val capacity = event.capacity
    val isFull = capacity != null && event.registeredCount >= capacity
    val hasStarted = event.startTime <= now

    Text(
        text = if (capacity != null) {
            stringResource(R.string.registration_count_limited, event.registeredCount, capacity)
        } else {
            pluralStringResource(R.plurals.registration_count, event.registeredCount, event.registeredCount)
        },
        style = MaterialTheme.typography.titleSmall,
    )

    when {
        // Organizator ne zauzima mesto, ali vidi ko dolazi
        isOwner -> OutlinedButton(
            onClick = onShowAttendees,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.attendees_show, event.registeredCount))
        }

        hasAttended -> Text(
            text = stringResource(R.string.attendance_confirmed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        AttendanceRules.canCheckIn(event, isRegistered, now) -> {
            Button(
                onClick = onCheckIn,
                enabled = !isCheckInPending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCheckInPending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.attendance_check_in))
                }
            }
            Text(
                text = if (isRegistered) {
                    stringResource(R.string.attendance_hint_registered, AttendanceRules.CHECK_IN_RADIUS_METERS)
                } else {
                    stringResource(
                        R.string.attendance_hint_walk_in,
                        AttendanceRules.WALK_IN_WINDOW_MINUTES,
                        AttendanceRules.CHECK_IN_RADIUS_METERS,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Prijavljen ovde stize tek posle kraja, ostali kad prodje prozor bez prijave
        hasStarted -> Text(
            text = stringResource(
                if (isRegistered) R.string.attendance_not_confirmed else R.string.registration_closed
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        isRegistered -> {
            Text(
                text = stringResource(R.string.registration_you_are_registered),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = onToggle,
                enabled = !isPending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.registration_cancel))
                }
            }
        }

        else -> Button(
            onClick = onToggle,
            enabled = !isPending && !isFull,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isPending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(if (isFull) R.string.registration_full else R.string.registration_register))
            }
        }
    }

    // Van grane dugmeta, jer odbijanje moze da zatvori dugme (npr. zauzeto poslednje mesto)
    if (!isOwner && !hasAttended) {
        checkInProblem?.let { checkInMessage(it) }?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun checkInMessage(problem: CheckInResult): String? = when (problem) {
    CheckInResult.Success -> null
    CheckInResult.NoLocation -> stringResource(R.string.attendance_error_no_location)
    CheckInResult.MockLocation -> stringResource(R.string.attendance_error_mock)
    is CheckInResult.Inaccurate -> stringResource(R.string.attendance_error_inaccurate, problem.accuracyMeters)
    is CheckInResult.TooFar -> stringResource(
        R.string.attendance_error_too_far,
        problem.distanceMeters,
        AttendanceRules.CHECK_IN_RADIUS_METERS,
    )
    CheckInResult.Closed -> stringResource(R.string.attendance_error_closed)
    CheckInResult.Full -> stringResource(R.string.attendance_error_full)
    CheckInResult.NoConnection -> stringResource(R.string.error_action_offline)
    CheckInResult.Failed -> stringResource(R.string.attendance_error_failed)
}

/** Spisak za organizatora u panelu odozdo; detalj ostaje ispod */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendeesSheet(
    state: UiState<List<Attendee>>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is UiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is UiState.Error -> {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.common_try_again))
                    }
                }

                is UiState.Success -> {
                    val attended = state.data.count { it.checkedInAt != null }
                    Text(
                        text = stringResource(R.string.attendees_title, state.data.size, attended),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    if (state.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.attendees_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items = state.data, key = { it.userId }) { attendee ->
                                AttendeeRow(attendee)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendeeRow(attendee: Attendee) {
    val checkedInAt = attendee.checkedInAt

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Skraceni id ako profil nema ime
                text = attendee.displayName ?: attendee.userId.take(8),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when {
                    checkedInAt == null ->
                        stringResource(R.string.attendees_registered_at, formatEventDateTime(attendee.registeredAt))
                    attendee.walkIn ->
                        stringResource(R.string.attendees_walk_in_at, formatEventDateTime(checkedInAt))
                    else ->
                        stringResource(R.string.attendees_checked_in_at, formatEventDateTime(checkedInAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (checkedInAt != null) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.attendance_confirmed),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
