package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.example.orbit.ui.components.EventFilterButton
import com.example.orbit.ui.components.EventFilterSheet
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.stateholders.SearchViewModel

/** Isti okvir za obe liste */
private val LIST_PADDING = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)

/**
 * F-12/F-17/F-29: Explore, jedina lista javnih dogadjaja.
 * Bez gornje trake i bez tabova: prijave i posecene nosi ekran Plans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onEventClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }

    // Ne pita odmah; filter trazi dozvolu kad zatreba
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::refreshLocation,
        askOnFirstAppearance = false,
    )

    Column(modifier = Modifier.fillMaxSize()) {

        SearchRow(
            query = filters.query,
            activeFilters = filters.activeCount,
            onQueryChange = viewModel::onQueryChange,
            onFiltersClick = { showFilters = true },
        )

        // Server nedostupan, ali prikazujemo kes
        syncError?.let { messageRes ->
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Povlacenje nadole osvezava; dugme u gornjoj traci vise ne postoji
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            AllEventsList(
                userNames = userNames,
                uiState = uiState,
                filters = filters,
                onRetry = viewModel::refresh,
                onEventClick = onEventClick,
            )
        }
    }

    if (showFilters) {
        EventFilterSheet(
            filters = filters,
            locationKnown = userLocation != null,
            onFiltersChange = { updated ->
                // Dozvola se trazi tek kad izabere filter udaljenosti
                if (!permission.granted && updated.needsLocation && !filters.needsLocation) {
                    permission.request()
                }
                viewModel.onFiltersChange(updated)
            },
            onDismiss = { showFilters = false },
        )
    }
}

/** Polje pretrage i dugme filtera u jednom redu, umesto dve trake jedna ispod druge */
@Composable
private fun SearchRow(
    query: String,
    activeFilters: Int,
    onQueryChange: (String) -> Unit,
    onFiltersClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_field_label)) },
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
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(1f),
        )

        EventFilterButton(activeCount = activeFilters, onClick = onFiltersClick)
    }
}

@Composable
private fun AllEventsList(
    userNames: Map<String, String>,
    uiState: UiState<List<Event>>,
    filters: EventFilters,
    onRetry: () -> Unit,
    onEventClick: (String) -> Unit,
) {
    val query = filters.query

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

@Composable
private fun EventList(
    events: List<Event>,
    userNames: Map<String, String>,
    onEventClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LIST_PADDING,
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
