package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.stateholders.EventListViewModel

/**
 * F-07 - the start screen.
 *
 * `collectAsStateWithLifecycle()` subscribes to the ViewModel's StateFlow and gives us a
 * normal Compose value. The "withLifecycle" part means it stops listening while the screen
 * is in the background, instead of quietly doing database work no one can see.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    onEventClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onMapClick: () -> Unit,
    viewModel: EventListViewModel = hiltViewModel<EventListViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orbit") },
                actions = {
                    IconButton(onClick = onMapClick) {
                        Icon(Icons.Filled.Place, contentDescription = "Show map")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Filled.Add, contentDescription = "Create event")
            }
        },
    ) { innerPadding ->

        // `when` on a sealed interface is exhaustive - the compiler makes sure every
        // state is handled, so a new state can never be silently forgotten.
        when (val state = uiState) {

            is UiState.Loading -> LoadingView(Modifier.padding(innerPadding))

            is UiState.Error -> ErrorView(
                message = state.message,
                modifier = Modifier.padding(innerPadding),
            )

            is UiState.Success ->
                if (state.data.isEmpty()) {
                    EmptyView(
                        title = "No events yet",
                        subtitle = "Tap + to create your first one.",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = innerPadding.calculateBottomPadding() + 88.dp,
                            start = 16.dp,
                            end = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // `key = { it.id }` matters: without it, Compose reuses rows by
                        // position and the scroll jumps whenever the list re-emits.
                        items(items = state.data, key = { it.id }) { event ->
                            EventRow(event = event, onClick = { onEventClick(event.id) })
                        }
                    }
                }
        }
    }
}
