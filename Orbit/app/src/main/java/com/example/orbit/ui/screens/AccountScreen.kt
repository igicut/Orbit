package com.example.orbit.ui.screens

import com.example.orbit.data.notification.EventReminderService
import com.example.orbit.data.notification.REMINDER_WINDOW_HOURS
import com.example.orbit.data.notification.ReminderOutcome
import com.example.orbit.ui.components.findActivity
import com.example.orbit.ui.components.openAppSettings
import androidx.core.app.ActivityCompat

import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.compose.rememberLauncherForActivityResult

import android.os.Build

import android.Manifest

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import androidx.compose.ui.platform.LocalContext

import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.EventRow
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.stateholders.AccountViewModel

/** Ekran naloga: moji i pridruzeni privatni dogadjaji */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onEventClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val joinedEvents by viewModel.joinedEvents.collectAsStateWithLifecycle()
    val showJoinDialog by viewModel.showJoinDialog.collectAsStateWithLifecycle()
    val joinedEventTitle by viewModel.joinedEventTitle.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val savedDisplayName by viewModel.savedDisplayName.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val userNames by viewModel.userNames.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Stringovi se citaju ovde, ne u callback-u (locale)
    val permissionDeniedMessage = stringResource(R.string.reminder_permission_denied)
    val permissionBlockedMessage = stringResource(R.string.reminder_permission_blocked)

    // F-26: Android 13+ trazi dozvolu za obavestenja
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when {
            granted -> EventReminderService.start(context)

            // Trajno odbijeno, preostaju samo podesavanja
            !canAskForNotifications(context) -> {
                Toast.makeText(context, permissionBlockedMessage, Toast.LENGTH_LONG).show()
                context.openAppSettings()
            }

            else -> Toast.makeText(context, permissionDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }

    // F-25: ishod provere kao stanje, poruka u kompoziciji
    var reminderOutcome by remember { mutableStateOf<ReminderOutcome?>(null) }

    LaunchedEffect(Unit) {
        viewModel.reminderOutcome.collect { reminderOutcome = it }
    }

    val reminderMessage = when (val outcome = reminderOutcome) {
        null -> null
        is ReminderOutcome.Posted ->
            pluralStringResource(R.plurals.reminder_posted, outcome.count, outcome.count)

        ReminderOutcome.AlreadyNotified ->
            stringResource(R.string.reminder_already_notified)

        ReminderOutcome.NothingSoon ->
            stringResource(R.string.reminder_nothing_soon, REMINDER_WINDOW_HOURS)

        ReminderOutcome.NoSavedEvents ->
            stringResource(R.string.reminder_no_saved_events)

        ReminderOutcome.PermissionMissing -> permissionDeniedMessage
        ReminderOutcome.Failed -> stringResource(R.string.reminder_failed)
    }

    LaunchedEffect(reminderMessage) {
        reminderMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            // Resetuj da se sledeca provera opet prikaze
            reminderOutcome = null
        }
    }

    // Toast umesto skoka na dogadjaj, pojavi se ispod
    val joinedMessage = joinedEventTitle?.let { stringResource(R.string.join_success, it) }

    LaunchedEffect(joinedMessage) {
        joinedMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.onJoinMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.account_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.account_create_event),
                )
            }
        },
    ) { innerPadding ->

        // Jedan LazyColumn, ugnjezdeni scroll-ovi bi se svadjali
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 88.dp, // da se ne preklopi sa FAB-om
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            item {
                // Nema logovanja, id uredjaja je identitet
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.account_this_device),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = viewModel.userId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // F-13: ime koje drugi vide uz dogadjaje
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = viewModel::onDisplayNameChange,
                            label = { Text(stringResource(R.string.account_display_name)) },
                            singleLine = true,
                            isError = nameError != null,
                            supportingText = {
                                Text(
                                    stringResource(
                                        nameError ?: R.string.account_display_name_hint
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Samo kad ima sta da se sacuva
                        if (displayName.trim() != savedDisplayName) {
                            TextButton(
                                onClick = viewModel::saveDisplayName,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.account_save_name))
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::openJoinDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.account_join_private))
                }
            }

            // F-25: rucna provera podsetnika preko servisa
            item {
                OutlinedButton(
                    onClick = {
                        val needsPermission =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !viewModel.canPostNotifications()

                        when {
                            !needsPermission -> EventReminderService.start(context)

                            // Trajno odbijanje se obradjuje u callback-u
                            else -> notificationPermission.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.account_check_reminders))
                }
            }

            // ---- dogadjaji napravljeni na ovom uredjaju ----
            item {
                Text(
                    text = stringResource(R.string.account_my_events),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            when (val state = uiState) {
                is UiState.Loading -> item { LoadingView() }
                is UiState.Error -> item {
                    ErrorView(message = stringResource(state.messageRes))
                }
                is UiState.Success ->
                    if (state.data.isEmpty()) {
                        item { SectionHint(stringResource(R.string.account_empty_subtitle)) }
                    } else {
                        items(items = state.data, key = { it.id }) { event ->
                            EventRow(event = event, onClick = { onEventClick(event.id) })
                        }
                    }
            }

            // ---- privatni dogadjaji pridruzeni kodom ----
            item {
                Text(
                    text = stringResource(R.string.account_joined_events),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (joinedEvents.isEmpty()) {
                item { SectionHint(stringResource(R.string.account_joined_empty)) }
            } else {
                items(items = joinedEvents, key = { "joined_" + it.id }) { event ->
                    EventRow(
                        event = event,
                        organiserName = userNames[event.ownerId],
                        onClick = { onEventClick(event.id) },
                    )
                }
            }

            // ---- F-28: blokirani korisnici ----
            item {
                Text(
                    text = stringResource(R.string.account_blocked_users),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (blockedUsers.isEmpty()) {
                item { SectionHint(stringResource(R.string.account_blocked_empty)) }
            } else {
                items(items = blockedUsers, key = { "blocked_" + it.blockedId }) { blocked ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(
                                start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // Ime ako ga imamo, inace skraceni id
                                text = blocked.displayName ?: blocked.blockedId.take(8),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            TextButton(onClick = { viewModel.unblock(blocked.blockedId) }) {
                                Text(stringResource(R.string.account_unblock))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        JoinPrivateEventDialog(viewModel)
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** F-21: dijalog za unos pristupnog koda */
@Composable
private fun JoinPrivateEventDialog(viewModel: AccountViewModel) {
    val code by viewModel.joinCode.collectAsStateWithLifecycle()
    val error by viewModel.joinError.collectAsStateWithLifecycle()
    val isJoining by viewModel.isJoining.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = viewModel::dismissJoinDialog,
        title = { Text(stringResource(R.string.join_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.join_dialog_text))

                OutlinedTextField(
                    value = code,
                    onValueChange = viewModel::onJoinCodeChange,
                    label = { Text(stringResource(R.string.join_code_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(stringResource(it)) } },
                    // Tastatura u velikim slovima, kodovi su uppercase
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = viewModel::join,
                enabled = !isJoining && code.isNotBlank(),
            ) {
                if (isJoining) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.join_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissJoinDialog) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** Da li sistem jos moze da prikaze dijalog */
private fun canAskForNotifications(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val activity = context.findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.POST_NOTIFICATIONS,
    )
}
