package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.domain.model.AttendedEvent
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventFilterBar
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.stateholders.EventsTab
import com.example.orbit.ui.stateholders.SearchViewModel
import com.example.orbit.ui.util.formatEventDateTime

/** F-12/F-17/F-29/F-36: ekran dogadjaja sa tabovima Svi, Prijavljeni i Poseceni */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onEventClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val registeredEvents by viewModel.registeredEvents.collectAsStateWithLifecycle()
    val attendedEvents by viewModel.attendedEvents.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()

    // Ne pita odmah; filter trazi dozvolu kad zatreba
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::refreshLocation,
        askOnFirstAppearance = false,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                actions = {
                    // Osvezava i javne dogadjaje i prijave naloga
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.search_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->

        Column(modifier = Modifier.padding(innerPadding)) {

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                EventsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            when (selectedTab) {
                EventsTab.ALL -> AllEventsList(
                    userNames = userNames,
                    uiState = uiState,
                    filters = filters,
                    locationKnown = userLocation != null,
                    canAskForLocation = !permission.granted,
                    isRefreshing = isRefreshing,
                    syncError = syncError,
                    onQueryChange = viewModel::onQueryChange,
                    onFiltersChange = viewModel::onFiltersChange,
                    onRequestLocation = permission.request,
                    onRetry = viewModel::refresh,
                    onEventClick = onEventClick,
                )

                EventsTab.REGISTERED -> RegisteredEventsList(
                    userNames = userNames,
                    events = registeredEvents,
                    onEventClick = onEventClick,
                )

                EventsTab.HISTORY -> HistoryList(
                    userNames = userNames,
                    attended = attendedEvents,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

@Composable
private fun AllEventsList(
    userNames: Map<String, String>,
    uiState: UiState<List<Event>>,
    filters: EventFilters,
    locationKnown: Boolean,
    canAskForLocation: Boolean,
    isRefreshing: Boolean,
    syncError: Int?,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (EventFilters) -> Unit,
    onRequestLocation: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
) {
    val query = filters.query

    Column {

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_field_label)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        EventFilterBar(
            filters = filters,
            locationKnown = locationKnown,
            onFiltersChange = { updated ->
                // Dozvola se trazi tek kad izabere filter udaljenosti
                if (canAskForLocation && updated.needsLocation && !filters.needsLocation) {
                    onRequestLocation()
                }
                onFiltersChange(updated)
            },
        )

        // Tanka traka, lista ostaje upotrebljiva
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Server nedostupan, ali prikazujemo kes
        syncError?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when (uiState) {
            is UiState.Loading -> LoadingView()

            is UiState.Error -> ErrorView(
                message = stringResource(uiState.messageRes),
                onRetry = onRetry,
            )

            is UiState.Success ->
                if (uiState.data.isEmpty()) {
                    // Tri razlicite prazne liste, sa razlicitom porukom
                    EmptyView(
                        title = stringResource(
                            when {
                                query.isNotBlank() -> R.string.search_no_matches_title
                                filters.activeCount > 0 -> R.string.search_filtered_out_title
                                else -> R.string.search_empty_title
                            }
                        ),
                        subtitle = when {
                            query.isNotBlank() ->
                                stringResource(R.string.search_no_matches_subtitle, query)

                            filters.activeCount > 0 ->
                                stringResource(R.string.search_filtered_out_subtitle)

                            else -> stringResource(R.string.search_empty_subtitle)
                        },
                    )
                } else {
                    EventList(
                        events = uiState.data,
                        userNames = userNames,
                        onEventClick = onEventClick,
                    )
                }
        }
    }
}

/** Otkazivanje je na detalju, jer oslobadja mesto */
@Composable
private fun RegisteredEventsList(
    userNames: Map<String, String>,
    events: List<Event>,
    onEventClick: (String) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.registered_empty_title),
            subtitle = stringResource(R.string.registered_empty_subtitle),
        )
    } else {
        EventList(
            events = events,
            userNames = userNames,
            onEventClick = onEventClick,
        )
    }
}

/** F-36: poseceni dogadjaji; ocenjuje se na detalju */
@Composable
private fun HistoryList(
    userNames: Map<String, String>,
    attended: List<AttendedEvent>,
    onEventClick: (String) -> Unit,
) {
    if (attended.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty_subtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = attended, key = { it.event.id }) { row ->
            val checkedIn = formatEventDateTime(row.checkedInAt)
            EventRow(
                event = row.event,
                organiserName = userNames[row.event.ownerId],
                note = row.myRating?.let { stringResource(R.string.history_attended_rated, checkedIn, it) }
                    ?: stringResource(R.string.history_attended_not_rated, checkedIn),
                onClick = { onEventClick(row.event.id) },
            )
        }
    }
}

@Composable
private fun EventList(
    events: List<Event>,
    userNames: Map<String, String>,
    onEventClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stabilan key da scroll ne skace posle sync-a
        items(items = events, key = { it.id }) { event ->
            EventRow(
                event = event,
                organiserName = userNames[event.ownerId],
                onClick = { onEventClick(event.id) },
            )
        }
    }
}
