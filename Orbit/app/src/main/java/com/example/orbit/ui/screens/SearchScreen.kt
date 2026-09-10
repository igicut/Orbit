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

/**
 * F-12 / F-17 / F-29 - the Events screen, with two lists behind a tab row.
 *
 *  All    - everything in Room: downloaded from the server plus created here,
 *           narrowed by the filter bar.
 *  Saved  - only what the user bookmarked, for a short list they care about.
 *
 * Both read from Room rather than from a network response, so both keep working
 * with no connection. Refreshing writes server results into Room and the lists
 * follow on their own.
 *
 * Location permission is requested lazily here, unlike on the map. This screen
 * is perfectly usable without it - it just shows everything instead of what is
 * nearby - so interrupting with a system dialog before the user has shown any
 * interest in distance would be asking for something they do not yet need. The
 * prompt comes when they reach for a distance filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onEventClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedEvents by viewModel.savedEvents.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()

    // askOnFirstAppearance = false, for the reason in the note above. request()
    // is handed down to the filter bar, which calls it at the moment the user
    // reaches for something that actually needs a position.
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::refreshLocation,
        askOnFirstAppearance = false,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                actions = {
                    // Only meaningful for the All list; the saved list is local.
                    if (selectedTab == EventsTab.ALL) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.search_refresh),
                            )
                        }
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

                EventsTab.SAVED -> SavedEventsList(
                    userNames = userNames,
                    events = savedEvents,
                    onEventClick = onEventClick,
                    onRemove = viewModel::unsaveEvent,
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
                // Choosing a distance-based option is the moment the permission
                // is actually needed, so that is when it gets asked for.
                if (canAskForLocation && updated.needsLocation && !filters.needsLocation) {
                    onRequestLocation()
                }
                onFiltersChange(updated)
            },
        )

        // A thin bar rather than a blocking spinner: the cached list stays
        // readable and usable while the refresh happens behind it.
        if (isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Reaching the server failed, but there is still cached data to show.
        // A notice, not an error state - the screen still works.
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
                    // Three different empty lists. Saying which one this is, is
                    // the difference between "there is nothing here" and "your
                    // filters hid it" - and only one of those needs action.
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

@Composable
private fun SavedEventsList(
    userNames: Map<String, String>,
    events: List<Event>,
    onEventClick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.saved_empty_title),
            subtitle = stringResource(R.string.saved_empty_subtitle),
        )
    } else {
        EventList(
            events = events,
            userNames = userNames,
            onEventClick = onEventClick,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun EventList(
    events: List<Event>,
    userNames: Map<String, String>,
    onEventClick: (String) -> Unit,
    onRemove: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // A stable key, or the scroll position jumps every time Room re-emits
        // after a sync.
        items(items = events, key = { it.id }) { event ->
            EventRow(
                event = event,
                organiserName = userNames[event.ownerId],
                onClick = { onEventClick(event.id) },
                onRemove = onRemove?.let { remove -> { remove(event.id) } },
            )
        }
    }
}
