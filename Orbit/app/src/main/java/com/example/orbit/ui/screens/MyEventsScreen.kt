package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.stateholders.AccountViewModel
import com.example.orbit.ui.stateholders.MyEventsTab
import kotlinx.coroutines.delay

private val LIST_PADDING = PaddingValues(16.dp)

/** Dogadjaj koji se zavrsi dok je lista otvorena sam prelazi u Completed */
private const val CLOCK_TICK_MS = 60_000L

/** Dogadjaji koje je napravio ovaj nalog; svoj ekran jer lista moze da naraste */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyEventsScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MyEventsTab.ONGOING) }

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }

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

            is UiState.Success -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    MyEventsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }

                // Isti uslov za oba taba, samo obrnut
                val wantEnded = selectedTab == MyEventsTab.COMPLETED
                val shown = state.data.filter { AttendanceRules.hasEnded(it, now) == wantEnded }

                MyEventsList(
                    events = shown,
                    tab = selectedTab,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

@Composable
private fun MyEventsList(
    events: List<Event>,
    tab: MyEventsTab,
    onEventClick: (String) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyView(
            title = stringResource(
                if (tab == MyEventsTab.COMPLETED) {
                    R.string.my_events_completed_empty_title
                } else {
                    R.string.account_empty_title
                }
            ),
            subtitle = stringResource(
                if (tab == MyEventsTab.COMPLETED) {
                    R.string.my_events_completed_empty_subtitle
                } else {
                    R.string.account_empty_subtitle
                }
            ),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = LIST_PADDING,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            EventRow(event = event, onClick = { onEventClick(event.id) })
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
