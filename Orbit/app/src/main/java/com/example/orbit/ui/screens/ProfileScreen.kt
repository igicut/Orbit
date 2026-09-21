package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
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
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.OrbitTopBar
import com.example.orbit.ui.navigation.systemNavSpace
import com.example.orbit.ui.stateholders.MyEventsTab
import com.example.orbit.ui.stateholders.ProfileViewModel
import java.util.Locale

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

    Scaffold(
        topBar = {
            OrbitTopBar(
                onNavigate = onBack,
                // Skraceni id dok profil ne stigne, isto kao na detalju
                title = user?.displayName ?: viewModel.userId.take(8),
            )
        },
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

            ProfileHeader(events = events, loadFailed = loadFailed)

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

/** Ukupna ocena i broj dogadjaja; ocena dolazi samo od potvrdjenih dolazaka */
@Composable
private fun ProfileHeader(events: List<Event>, loadFailed: Boolean) {
    val rating = organiserRating(events)

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_rating_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (rating == null) {
                Text(
                    text = stringResource(R.string.detail_rating_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(
                        R.string.detail_rating_value,
                        String.format(Locale.getDefault(), "%.1f", rating.average),
                        rating.count,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Text(
            text = stringResource(R.string.profile_event_count, events.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            EventRow(event = event, onClick = { onEventClick(event.id) })
        }
    }
}
