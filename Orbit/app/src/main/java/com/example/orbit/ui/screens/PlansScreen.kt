package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.ui.navigation.orbitBottomBarSpace
import com.example.orbit.domain.model.AttendedEvent
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.stateholders.PlansTab
import com.example.orbit.ui.stateholders.PlansViewModel
import com.example.orbit.ui.util.formatEventDateTime

/** Dno nosi visinu plutajuce trake, jer lista ide ispod nje */
private val listPadding: PaddingValues
    @Composable get() = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = 16.dp + orbitBottomBarSpace,
    )

/**
 * Sopstveni plan korisnika: sta dolazi i gde je vec bio.
 * Odvojeno od Explore, jer je Explore tudje, a ovo je moje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onEventClick: (String) -> Unit,
    viewModel: PlansViewModel = hiltViewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val upcoming by viewModel.upcomingEvents.collectAsStateWithLifecycle()
    val attended by viewModel.attendedEvents.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {

        // Providno, da tabovi ne prave svetlu prugu preko kremaste podloge
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                ) {
            PlansTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = { Text(stringResource(tab.labelRes)) },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (selectedTab) {
                PlansTab.UPCOMING -> UpcomingList(
                    events = upcoming,
                    userNames = userNames,
                    onEventClick = onEventClick,
                )

                PlansTab.ATTENDED -> AttendedList(
                    attended = attended,
                    userNames = userNames,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

/** Otkazivanje je na detalju, jer oslobadja mesto */
@Composable
private fun UpcomingList(
    events: List<Event>,
    userNames: Map<String, String>,
    onEventClick: (String) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyView(
            title = stringResource(R.string.registered_empty_title),
            subtitle = stringResource(R.string.registered_empty_subtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = events, key = { it.id }) { event ->
            EventRow(
                event = event,
                organiserName = userNames[event.ownerId],
                onClick = { onEventClick(event.id) },
            )
        }
    }
}

/** F-36: poseceni dogadjaji; ocenjuje se na detalju */
@Composable
private fun AttendedList(
    attended: List<AttendedEvent>,
    userNames: Map<String, String>,
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
        contentPadding = listPadding,
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
