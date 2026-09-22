package com.example.orbit.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.data.repository.CheckInResult
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventEditRules
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.domain.model.Rating
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.CategoryPill
import com.example.orbit.ui.components.DateTile
import com.example.orbit.ui.components.DetailTab
import com.example.orbit.ui.components.EntryQrSheet
import com.example.orbit.ui.components.scanQrCode
import com.example.orbit.ui.components.DetailTabRow
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventMetaBadges
import com.example.orbit.ui.components.EventOverviewTab
import com.example.orbit.ui.components.EventPhotoPager
import com.example.orbit.ui.components.EventReviewsTab
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.NoteBadge
import com.example.orbit.ui.components.OrbitTopBar
import com.example.orbit.ui.components.PhotoPlaceholder
import com.example.orbit.ui.components.SmallStars
import com.example.orbit.ui.components.eventAccent
import com.example.orbit.ui.components.eventBackground
import com.example.orbit.ui.components.filledStars
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.navigation.systemNavSpace
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Prozori prijave i dolaska zavise od sata, pa se detalj sam osvezava */
private const val CLOCK_TICK_MS = 30_000L

/** Koliko boje ulazi u baner otkazivanja; svetla podloga, tekst ostaje citljiv */
private const val BANNER_TINT = 0.18f

/** Sporedni tekst na obojenom vrhu; proveren na najjacoj boji kategorije (4.7:1) */
private const val SECONDARY_ALPHA = 0.8f

/** Fotografije su vise nego na kartici; ovo je glavni ekran dogadjaja */
private val HERO_PHOTO_HEIGHT = 200.dp

/** Ugao fotografije prati karticu: 20 dp spolja minus ram od 8 dp */
private val HERO_PHOTO_SHAPE = RoundedCornerShape(12.dp)

/** Mesto tabova u listi: vrh je 0, tabovi 1, sadrzaj izabranog taba 2 */
private const val TABS_INDEX = 1

/** Stanje prijave; od njega zavise glavno dugme i tekst ispod njega */
private enum class RegistrationStep {
    OWNER,
    CANCELLED,
    ATTENDED,
    CHECK_IN,
    CLOSED,
    REGISTERED,
    OPEN,
}

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
    val entryQr by viewModel.entryQr.collectAsStateWithLifecycle()
    var showEntryQr by remember { mutableStateOf(false) }
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
                        onScanQr = {
                            scanQrCode(
                                context = context,
                                onScanned = viewModel::checkInWithQr,
                                onUnavailable = viewModel::onQrScannerUnavailable,
                            )
                        },
                        onShowEntryQr = {
                            showEntryQr = true
                            viewModel.loadEntryQr()
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

    // F-41: panel se otvara samo sa ucitanog dogadjaja, jer mu trebaju id i naslov
    val qrEvent = (uiState as? UiState.Success)?.data
    if (showEntryQr && qrEvent != null) {
        EntryQrSheet(
            state = entryQr,
            eventId = qrEvent.id,
            eventTitle = qrEvent.title,
            onRetry = viewModel::loadEntryQr,
            onDismiss = { showEntryQr = false },
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
    onScanQr: () -> Unit,
    onShowAttendees: () -> Unit,
    onShowEntryQr: () -> Unit,
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

    val accent = eventAccent(event)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(DetailTab.OVERVIEW) }

    // Pamti stanje taba dok je sakriven, npr. zapocet komentar
    val tabStates = rememberSaveableStateHolder()

    // Kad su tabovi zalepljeni za vrh, novi tab krece od svog pocetka, ne od sredine
    LaunchedEffect(selectedTab) {
        if (listState.firstVisibleItemIndex >= TABS_INDEX) {
            listState.scrollToItem(TABS_INDEX)
        }
    }

    // Jedna lista: vrh odlazi sa skrolom, a tabovi ostaju zalepljeni gore
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + systemNavSpace),
    ) {
        item(key = "hero") {
            DetailHero(
                event = event,
                accent = accent,
                onOpenReviews = {
                    selectedTab = DetailTab.REVIEWS
                    scope.launch { listState.animateScrollToItem(TABS_INDEX) }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                DetailActions(
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
                    onScanQr = onScanQr,
                    onShowAttendees = onShowAttendees,
                    onNavigate = {
                        if (!openInMaps(context, event)) {
                            Toast.makeText(context, noMapsAppMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }

        stickyHeader(key = "tabs") {
            // Pozadina ekrana, da sadrzaj koji prolazi ispod tabova ne proviruje
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                DetailTabRow(
                    selected = selectedTab,
                    reviewCount = reviews.size,
                    accent = accent,
                    onSelect = { selectedTab = it },
                )
            }
        }

        item(key = "content") {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { tabTransition(forward = targetState.ordinal > initialState.ordinal) },
                modifier = Modifier.padding(horizontal = 16.dp),
                label = "detailTab",
            ) { tab ->
                tabStates.SaveableStateProvider(tab.name) {
                    when (tab) {
                        DetailTab.OVERVIEW -> EventOverviewTab(
                            event = event,
                            accent = accent,
                            now = now,
                            isOwner = isOwner,
                            organiserName = organiserName,
                            isOrganiserBlocked = isOrganiserBlocked,
                            onToggleBlock = onToggleBlock,
                            onOrganiserClick = onOrganiserClick,
                            onCancelEvent = onCancelEvent,
                            onShowEntryQr = onShowEntryQr,
                        )

                        DetailTab.REVIEWS -> EventReviewsTab(
                            event = event,
                            now = now,
                            isOwner = isOwner,
                            hasAttended = hasAttended,
                            reviews = reviews,
                            currentUserId = currentUserId,
                            myRating = myRating,
                            isReviewPending = isReviewPending,
                            ratingError = ratingError,
                            onSubmitReview = onSubmitReview,
                        )
                    }
                }
            }
        }
    }
}

/** Novi tab ulazi sa strane na koju se ide, stari bledi */
private fun tabTransition(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (slideInHorizontally { width -> direction * width / 8 } + fadeIn()) togetherWith
        (slideOutHorizontally { width -> -direction * width / 8 } + fadeOut())
}

/**
 * Vrh detalja na istoj pozadini kao kartica iz liste: fotografije, naslov, ocena, stanje
 * i glavna dugmad. Tako se vidi da je detalj ista kartica, samo otvorena.
 */
@Composable
private fun DetailHero(
    event: Event,
    accent: Color,
    onOpenReviews: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val isCancelled = event.status == EventStatus.CANCELLED
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .fillMaxWidth()
            .warmShadow(
                elevation = 6.dp,
                shape = shape,
                color = lerp(MaterialTheme.orbitAccents.shadow, accent, 0.5f),
            )
            .clip(shape)
            .background(eventBackground(accent))
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HERO_PHOTO_HEIGHT)
                .clip(HERO_PHOTO_SHAPE),
        ) {
            if (event.imageUris.isEmpty()) {
                PhotoPlaceholder(category = event.category, accent = accent, modifier = Modifier.matchParentSize())
            } else {
                EventPhotoPager(
                    paths = event.imageUris,
                    grayscale = isCancelled,
                    modifier = Modifier.matchParentSize(),
                )
            }

            DateTile(
                millis = event.startTime,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
            CategoryPill(
                category = event.category,
                color = accent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (isCancelled) {
                CancelledBanner(reason = event.cancelReason)
            }

            RatingLine(average = event.avgRating, count = event.ratingCount, onClick = onOpenReviews)

            EventMetaBadges(event = event)

            actions()
        }
    }
}

/** Ocena kao u mapama: broj, zvezdice i broj ocena; dodir otvara tab Utisci */
@Composable
private fun RatingLine(average: Float, count: Int, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA)

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.detail_reviews_open), onClick = onClick)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (count == 0) {
            Text(
                text = stringResource(R.string.detail_rating_none),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
            )
        } else {
            Text(
                text = String.format(Locale.getDefault(), "%.1f", average),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SmallStars(value = filledStars(average))
            Text(text = "($count)", style = MaterialTheme.typography.bodyMedium, color = secondary)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Glavna dugmad u jednom redu: dugme koje zavisi od stanja prijave i navigacija pored njega.
 * Ispod stoje objasnjenje i greska potvrde dolaska.
 */
@Composable
private fun DetailActions(
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
    onScanQr: () -> Unit,
    onShowAttendees: () -> Unit,
    onNavigate: () -> Unit,
) {
    val capacity = event.capacity
    val isFull = capacity != null && event.registeredCount >= capacity
    val hintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA)

    // Prvo stanje koje vazi odlucuje; redosled provera je isti kao ranije
    val step = when {
        isOwner -> RegistrationStep.OWNER
        event.status == EventStatus.CANCELLED -> RegistrationStep.CANCELLED
        hasAttended -> RegistrationStep.ATTENDED
        AttendanceRules.canCheckIn(event, isRegistered, now) -> RegistrationStep.CHECK_IN
        event.startTime <= now -> RegistrationStep.CLOSED
        isRegistered -> RegistrationStep.REGISTERED
        else -> RegistrationStep.OPEN
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Potvrda stanja, isti zeleni bedz kao u istoriji
        when (step) {
            RegistrationStep.ATTENDED -> NoteBadge(stringResource(R.string.attendance_confirmed))
            RegistrationStep.REGISTERED -> NoteBadge(stringResource(R.string.registration_you_are_registered))
            else -> Unit
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mainButton = Modifier.weight(1f)

            when (step) {
                // Organizator ne zauzima mesto, ali vidi ko dolazi
                RegistrationStep.OWNER -> Button(onClick = onShowAttendees, modifier = mainButton) {
                    Text(stringResource(R.string.attendees_show, event.registeredCount))
                }

                RegistrationStep.CHECK_IN -> Button(
                    onClick = onCheckIn,
                    enabled = !isCheckInPending,
                    modifier = mainButton,
                ) {
                    ButtonLabel(isPending = isCheckInPending, text = stringResource(R.string.attendance_check_in))
                }

                RegistrationStep.REGISTERED -> OutlinedButton(
                    onClick = onToggle,
                    enabled = !isPending,
                    modifier = mainButton,
                ) {
                    ButtonLabel(isPending = isPending, text = stringResource(R.string.registration_cancel))
                }

                RegistrationStep.OPEN -> Button(
                    onClick = onToggle,
                    enabled = !isPending && !isFull,
                    modifier = mainButton,
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
                    ButtonLabel(
                        isPending = isPending,
                        text = stringResource(if (isFull) R.string.registration_full else R.string.registration_register),
                    )
                }

                // Nema sta da se ponudi; objasnjenje je u bedzu ili tekstu
                RegistrationStep.CANCELLED, RegistrationStep.ATTENDED, RegistrationStep.CLOSED -> Unit
            }

            // Bez glavnog dugmeta navigacija dobija celu sirinu
            val navigateAlone = step == RegistrationStep.CANCELLED ||
                step == RegistrationStep.ATTENDED ||
                step == RegistrationStep.CLOSED
            WhitePillButton(
                icon = painterResource(R.drawable.ic_directions),
                text = stringResource(R.string.detail_navigate),
                onClick = onNavigate,
                modifier = if (navigateAlone) Modifier.weight(1f) else Modifier,
            )
        }

        // F-41: druga vrata za potvrdu, kad GPS ne radi (zatvoren prostor); ista bela pilula kao navigacija
        if (step == RegistrationStep.CHECK_IN) {
            WhitePillButton(
                icon = painterResource(R.drawable.ic_qr_code),
                text = stringResource(R.string.attendance_scan_qr),
                onClick = onScanQr,
                enabled = !isCheckInPending,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (step) {
            RegistrationStep.CHECK_IN -> Text(
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
                color = hintColor,
            )

            // Prijavljen ovde stize tek posle kraja, ostali kad prodje prozor bez prijave
            RegistrationStep.CLOSED -> Text(
                text = stringResource(
                    if (isRegistered) R.string.attendance_not_confirmed else R.string.registration_closed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = hintColor,
            )

            else -> Unit
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
}

/** Tekst dugmeta, ili kruzic dok zahtev traje */
@Composable
private fun ButtonLabel(isPending: Boolean, text: String) {
    if (isPending) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        Text(text)
    }
}

/** Bela pilula sa ikonicom (navigacija, skeniranje QR-a); ne takmici se bojom sa glavnim dugmetom */
@Composable
private fun WhitePillButton(
    icon: Painter,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

/** F-19: otvara aplikaciju za mape preko geo: URI; false ako takve aplikacije nema */
private fun openInMaps(context: Context, event: Event): Boolean {
    val uri = Uri.parse(
        "geo:" + event.latitude + "," + event.longitude +
            "?q=" + event.latitude + "," + event.longitude +
            "(" + Uri.encode(event.title) + ")"
    )
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: ActivityNotFoundException) {
        false
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
    CheckInResult.WrongCode -> stringResource(R.string.attendance_error_wrong_qr)
    CheckInResult.ScannerUnavailable -> stringResource(R.string.attendance_error_scanner)
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
