package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.ui.navigation.systemNavSpace
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventCard
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.components.OrbitTopBar
import com.example.orbit.ui.stateholders.AccountViewModel
import com.example.orbit.ui.stateholders.MyEventsTab
import kotlinx.coroutines.delay

/** Dno nosi razmak za sistemsku navigaciju, jer sadrzaj ide do ivice ekrana */
private val listPadding: PaddingValues
    @Composable get() = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 16.dp,
        bottom = 16.dp + systemNavSpace,
    )

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
            OrbitTopBar(
                onNavigate = onBack,
                title = stringResource(R.string.account_my_events),
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
                // Providno, da tabovi ne prave svetlu prugu preko kremaste podloge
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                ) {
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
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            EventCard(event = event, onClick = { onEventClick(event.id) })
        }
    }
}
