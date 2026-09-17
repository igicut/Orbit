package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.stateholders.AccountViewModel

/** Dogadjaji koje je napravio ovaj nalog; svoj ekran jer lista moze da naraste */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyEventsScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_my_events)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> LoadingView(Modifier.padding(innerPadding))

            is UiState.Error -> ErrorView(
                message = stringResource(state.messageRes),
                modifier = Modifier.padding(innerPadding),
            )

            is UiState.Success -> if (state.data.isEmpty()) {
                EmptyView(
                    title = stringResource(R.string.account_empty_title),
                    subtitle = stringResource(R.string.account_empty_subtitle),
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.data, key = { it.id }) { event ->
                        EventRow(event = event, onClick = { onEventClick(event.id) })
                    }
                }
            }
        }
    }
}

/** Strelica nazad je ista na sva tri ekrana sa naloga */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.detail_back),
        )
    }
}
