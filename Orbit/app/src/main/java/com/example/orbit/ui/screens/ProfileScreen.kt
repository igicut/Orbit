package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.organiserRating
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.EventCard
import com.example.orbit.ui.components.OrbitTopBar
import com.example.orbit.ui.components.RatingStatTile
import com.example.orbit.ui.components.StatTile
import com.example.orbit.ui.components.UserHeaderCard
import com.example.orbit.ui.navigation.systemNavSpace
import com.example.orbit.ui.stateholders.MyEventsTab
import com.example.orbit.ui.stateholders.ProfileViewModel

/**
 * Profil organizatora: ukupna ocena i njegovi dogadjaji u dve liste.
 * Prosli dogadjaji nisu u pretrazi, pa je ovo put do njihovih utisaka za one koji nisu bili.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(MyEventsTab.ONGOING) }

    // Jednom po otvaranju; zavrsetak u toku gledanja nije bitan za profil
    val now = remember { System.currentTimeMillis() }

    // Skraceni id dok profil ne stigne, isto kao na detalju
    val name = user?.displayName ?: viewModel.userId.take(8)

    Scaffold(
        // Ime je u zaglavlju ispod, pa traka nosi samo strelicu nazad
        topBar = { OrbitTopBar(onNavigate = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isBlocked) {
                // F-28: blokiran nalog se ne prikazuje, kao ni njegovi dogadjaji u pretrazi
                EmptyView(
                    title = stringResource(R.string.profile_blocked_title),
                    subtitle = stringResource(R.string.profile_blocked_subtitle),
                )
                return@Column
            }

            ProfileHeader(name = name, events = events, loadFailed = loadFailed)

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

            // Buduci od najblizeg, prosli od najnovijeg
            val shown = if (selectedTab == MyEventsTab.COMPLETED) {
                events.filter { AttendanceRules.hasEnded(it, now) }.sortedByDescending { it.startTime }
            } else {
                events.filter { !AttendanceRules.hasEnded(it, now) }.sortedBy { it.startTime }
            }

            ProfileEventList(events = shown, tab = selectedTab, onEventClick = onEventClick)
        }
    }
}

/** Isto zaglavlje kao na nalogu; ocena dolazi samo od potvrdjenih dolazaka */
@Composable
private fun ProfileHeader(name: String, events: List<Event>, loadFailed: Boolean) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserHeaderCard(name = name) {
            RatingStatTile(rating = organiserRating(events), modifier = Modifier.weight(1f))
            StatTile(
                value = events.size.toString(),
                label = stringResource(R.string.stat_public_events),
                modifier = Modifier.weight(1f),
            )
        }

        if (loadFailed) {
            Text(
                text = stringResource(R.string.profile_load_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProfileEventList(
    events: List<Event>,
    tab: MyEventsTab,
    onEventClick: (String) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyView(
            title = stringResource(
                if (tab == MyEventsTab.COMPLETED) R.string.profile_empty_past
                else R.string.profile_empty_upcoming
            ),
            subtitle = stringResource(
                if (tab == MyEventsTab.COMPLETED) R.string.profile_empty_past_subtitle
                else R.string.profile_empty_upcoming_subtitle
            ),
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            // Sadrzaj ide do ivice ekrana, pa dno nosi razmak za sistemsku navigaciju
            bottom = 16.dp + systemNavSpace,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            EventCard(event = event, onClick = { onEventClick(event.id) })
        }
    }
}
