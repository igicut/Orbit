package com.example.orbit.ui.screens

import com.example.orbit.domain.model.EventEditRules

import androidx.compose.material.icons.filled.Edit

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import com.example.orbit.ui.components.EventPhotoRow
import com.example.orbit.ui.components.OrbitTopBar
import com.example.orbit.ui.components.ReviewForm
import com.example.orbit.ui.components.ReviewList


import androidx.compose.ui.res.stringResource

import com.example.orbit.R
import com.example.orbit.ui.navigation.systemNavSpace

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.example.orbit.ui.util.formatEventDateTimeShort
import java.util.Locale
import kotlin.math.ceil
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
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.data.repository.CheckInResult
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.Rating
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.util.formatEventDate
import com.example.orbit.ui.util.formatEventDateTime
import com.example.orbit.ui.util.formatEventTime
import kotlinx.coroutines.delay

/** Prozori prijave i dolaska zavise od sata, pa se detalj sam osvezava */
private const val CLOCK_TICK_MS = 30_000L

/** Koliko boje ulazi u baner otkazivanja; svetla podloga, tekst ostaje citljiv */
private const val BANNER_TINT = 0.18f

/** Koliko boje kategorije ulazi u bedz; na 0.38 tekst drzi bar 6.7:1 */
private const val CATEGORY_TINT = 0.38f

/** F-09/F-19: detalji dogadjaja i navigacija */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOrganiserClick: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel<EventDetailViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    val isRegistered by viewModel.isRegistered.collectAsStateWithLifecycle()
    val isRegistrationPending by viewModel.isRegistrationPending.collectAsStateWithLifecycle()
    val hasAttended by viewModel.hasAttended.collectAsStateWithLifecycle()
    val isCheckInPending by viewModel.isCheckInPending.collectAsStateWithLifecycle()
    val checkInProblem by viewModel.checkInProblem.collectAsStateWithLifecycle()
    val attendees by viewModel.attendees.collectAsStateWithLifecycle()
    var showAttendees by remember { mutableStateOf(false) }
    val myRating by viewModel.myRating.collectAsStateWithLifecycle()
    val ratingError by viewModel.ratingError.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val isReviewPending by viewModel.isReviewPending.collectAsStateWithLifecycle()
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

    val cancelError by viewModel.cancelError.collectAsStateWithLifecycle()
    val cancelErrorMessage = cancelError?.let { stringResource(it) }
    LaunchedEffect(cancelErrorMessage) {
        cancelErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearCancelError()
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
            // Naslov je u telu ekrana, pa traka ostaje bez njega
            OrbitTopBar(
                onNavigate = onBack,
                actions = {
                    // F-09: samo vlasnik; izmena i brisanje nestaju kad dogadjaj pocne,
                    // jer brisanje odnosi dolaske i ocene gostiju (server isto proverava)
                    if (isOwner && event != null &&
                        !EventEditRules.hasStarted(event)
                    ) {
                        // F-39: otkazan dogadjaj se ne menja, server vraca 409
                        if (event.status == EventStatus.ACTIVE) {
                            IconButton(onClick = { onEdit(event.id) }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.detail_edit),
                                )
                            }
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
                        onCancelEvent = { showCancelDialog = true },
                        onOrganiserClick = { onOrganiserClick(loaded.ownerId) },
                        ratingError = ratingError,
                        reviews = reviews,
                        currentUserId = viewModel.currentUserId,
                        isReviewPending = isReviewPending,
                        onSubmitReview = viewModel::submitReview,
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

    if (showCancelDialog) {
        val isCancelling by viewModel.isCancelling.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.detail_cancel_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.detail_cancel_message))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text(stringResource(R.string.detail_cancel_reason_hint)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCancelling,
                    onClick = {
                        viewModel.cancelEvent(cancelReason.trim().ifEmpty { null })
                        showCancelDialog = false
                    },
                ) {
                    Text(stringResource(R.string.detail_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.detail_cancel_dismiss))
                }
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
    reviews: List<Rating>,
    currentUserId: String,
    isReviewPending: Boolean,
    onSubmitReview: (value: Int, comment: String, image: String?) -> Unit,
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
    onCancelEvent: () -> Unit,
    onOrganiserClick: () -> Unit,
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
            // Dno nosi razmak za sistemsku navigaciju, sadrzaj ide do ivice ekrana
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + systemNavSpace),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        if (event.imageUris.isNotEmpty()) {
            EventPhotoRow(paths = event.imageUris)
        }

        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (event.status == EventStatus.CANCELLED) {
            CancelledBanner(reason = event.cancelReason)
        }

        Text(event.description, style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()

        // F-35: kraj se pokazuje samo kad je trajanje zadato
        MetadataRow(icon = Icons.Filled.DateRange, text = eventDateRange(event))
        event.address?.let { MetadataRow(icon = Icons.Filled.Place, text = it) }

        // Cena i pristupni kod; besplatno takodje ima svoj bedz da red ne izgleda prazno
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Kategorija je ranije bila boja gornje trake; traka je sada providna, pa boja stoji ovde
            DetailBadge(
                text = stringResource(event.category.labelRes()),
                container = lerp(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.orbitAccents.forCategory(event.category),
                    CATEGORY_TINT,
                ),
                content = MaterialTheme.colorScheme.onSurface,
            )
            DetailBadge(
                text = event.price?.let { stringResource(R.string.detail_price, formatPrice(it)) }
                    ?: stringResource(R.string.detail_price_free),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            event.accessCode?.let {
                DetailBadge(
                    text = stringResource(R.string.detail_access_code, it),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        AverageRating(average = event.avgRating, count = event.ratingCount)

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

        // F-39: samo vlasnik, dok dogadjaj traje i dok nije vec otkazan
        if (isOwner && event.status == EventStatus.ACTIVE && !AttendanceRules.hasEnded(event, now)) {
            OutlinedButton(
                onClick = onCancelEvent,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.orbitAccents.noSpots,
                ),
            ) {
                Text(stringResource(R.string.detail_cancel))
            }
        }

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
                // Ime vodi na profil organizatora; strelica kaze da je red klikabilan
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClickLabel = stringResource(R.string.detail_organiser_open),
                            onClick = onOrganiserClick,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // F-27/F-40: utisci; pre pocetka ih ne moze biti, pa se ni ne prikazuju
        if (event.startTime <= now) {
            HorizontalDivider()

            Text(
                text = stringResource(R.string.reviews_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // Ocenjuju samo potvrdjeni dolasci, server isto proverava; organizator samo cita
            if (!isOwner) {
                if (hasAttended) {
                    ReviewForm(
                        myReview = reviews.firstOrNull { it.userId == currentUserId },
                        myStars = myRating,
                        isPending = isReviewPending,
                        error = ratingError,
                        onSubmit = onSubmitReview,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.rating_requires_attendance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Sopstveni utisak je vec u formi iznad, u listi bi stajao dvaput
            ReviewList(reviews = reviews.filter { it.userId != currentUserId })
        }
    }
}

/** Red metapodatka: ikonica pa tekst, bez recenice u labeli */
@Composable
private fun MetadataRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Pocetak i kraj u jednom redu; bez trajanja ostaje samo pocetak */
@Composable
private fun eventDateRange(event: Event): String {
    val start = formatEventDateTimeShort(event.startTime)
    if (event.durationMinutes == null) return start

    val endTime = AttendanceRules.endTime(event)
    val end = if (formatEventDate(endTime) == formatEventDate(event.startTime)) {
        formatEventTime(endTime)
    } else {
        formatEventDateTimeShort(endTime)
    }
    return "$start  →  $end"
}

/** Cela cena bez decimala; 500.0 je izgledalo kao greska */
private fun formatPrice(price: Double): String =
    if (price % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f", price)
    } else {
        String.format(Locale.getDefault(), "%.2f", price)
    }

@Composable
private fun DetailBadge(text: String, container: Color, content: Color) {
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Prosek: zvezdice uz broj; sitne i nekliktabilne, da se ne pomesaju sa unosom ocene */
@Composable
private fun AverageRating(average: Float, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_rating_average),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (count == 0) {
            Text(
                text = stringResource(R.string.detail_rating_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }

        Row {
            // Polovina se ne racuna kao puna zvezdica: 4.5 daje cetiri, 4.6 pet
            val filled = ceil(average - 0.5f).toInt()
            (1..5).forEach { star ->
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (star <= filled) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = stringResource(
                R.string.detail_rating_value,
                String.format(Locale.getDefault(), "%.1f", average),
                count,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Kapacitet: traka koja ide ka terakoti kako se puni; bez kapaciteta samo broj prijava */
@Composable
private fun CapacityBlock(taken: Int, capacity: Int?) {
    if (capacity == null) {
        Text(
            text = pluralStringResource(R.plurals.registration_count, taken, taken),
            style = MaterialTheme.typography.titleSmall,
        )
        return
    }

    val fraction = (taken.toFloat() / capacity).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.registration_count_limited, taken, capacity),
            style = MaterialTheme.typography.titleSmall,
        )
        LinearProgressIndicator(
            progress = { fraction },
            color = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error, fraction),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
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

    CapacityBlock(taken = event.registeredCount, capacity = capacity)

    when {
        // Organizator ne zauzima mesto, ali vidi ko dolazi
        isOwner -> OutlinedButton(
            onClick = onShowAttendees,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.attendees_show, event.registeredCount))
        }

        // Baner iznad vec objasnjava; ovde nema sta da se ponudi
        event.status == EventStatus.CANCELLED -> {}

        hasAttended -> Text(
            text = stringResource(R.string.attendance_confirmed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.orbitAccents.registered,
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
                color = MaterialTheme.orbitAccents.registered,
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
            // Popunjeno nije greska u radu, ali jeste odbijanje; zato terakota a ne siva
            colors = if (isFull) {
                ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
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

/** F-39: otkazan dogadjaj; razlog samo kad ga je organizator upisao */
@Composable
private fun CancelledBanner(reason: String?) {
    Surface(
        color = MaterialTheme.orbitAccents.noSpots.copy(alpha = BANNER_TINT),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_cancelled_banner),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            reason?.let {
                Text(
                    text = stringResource(R.string.detail_cancelled_reason, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
