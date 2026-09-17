package com.example.orbit.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.stateholders.AccountViewModel

/**
 * Privatni dogadjaji u koje se uslo kodom.
 * Kad je prazno, dugme za unos koda stoji tu, a ne kao zasebno dugme na nalogu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinedEventsScreen(
    onBack: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val joinedEvents by viewModel.joinedEvents.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()
    val showJoinDialog by viewModel.showJoinDialog.collectAsStateWithLifecycle()
    val joinedEventTitle by viewModel.joinedEventTitle.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Novi dogadjaj se pojavi u listi ispod, poruka samo potvrdjuje
    val joinedMessage = joinedEventTitle?.let { stringResource(R.string.join_success, it) }
    LaunchedEffect(joinedMessage) {
        joinedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.onJoinMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_joined_events)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        if (joinedEvents.isEmpty()) {
            EmptyView(
                title = stringResource(R.string.account_joined_events),
                subtitle = stringResource(R.string.account_joined_empty),
                modifier = Modifier.padding(innerPadding),
                action = {
                    Button(onClick = viewModel::openJoinDialog) {
                        Text(stringResource(R.string.account_join_private))
                    }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = joinedEvents, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        organiserName = userNames[event.ownerId],
                        onClick = { onEventClick(event.id) },
                    )
                }

                // Kod moze da se unese i kad lista nije prazna
                item {
                    Button(
                        onClick = viewModel::openJoinDialog,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.account_join_private))
                    }
                }
            }
        }
    }

    if (showJoinDialog) JoinPrivateEventDialog(viewModel)
}
