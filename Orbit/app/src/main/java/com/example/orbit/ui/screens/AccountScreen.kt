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

/**
 * The account screen: what this device has created, and the private events it
 * has been let into.
 *
 * The two lists are separate on purpose. "My events" are yours to edit or
 * delete; joined ones belong to somebody else and you only have read access.
 * Mixing them would suggest a control you do not have.
 *
 * Creating and joining both live here rather than on the Events tab, which is
 * for discovering other people's public events.
 */
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

    // Every user-visible string is resolved here, in composition, rather than
    // inside the callbacks below. A Context captured in a lambda keeps the
    // configuration it was created with, so context.getString() from a callback
    // can produce the previous language after a locale change.
    val permissionDeniedMessage = stringResource(R.string.reminder_permission_denied)
    val permissionBlockedMessage = stringResource(R.string.reminder_permission_blocked)

    // F-26 - Android 13+ refuses to show notifications until this is granted.
    // Registered during composition, for the same reason as the camera launcher.
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            EventReminderService.start(context)
        } else {
            Toast.makeText(context, permissionDeniedMessage, Toast.LENGTH_LONG).show()
        }
    }

    // F-25 - every check now ends in a sentence. Previously all of the endings
    // looked identical from the outside (nothing happened), which is exactly why
    // a check that was working looked broken.
    //
    // The outcome is held as state so the message can be resolved in
    // composition; translating inside the collect lambda would reintroduce the
    // stale-locale problem described above.
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
            // Cleared so the next check announces itself even when the outcome
            // is the same as last time.
            reminderOutcome = null
        }
    }

    // A toast rather than opening the event: joining is a small confirmation,
    // and being thrown onto another screen mid-task is more disruptive than
    // helpful. The event appears in the Joined section just below.
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

        // One LazyColumn holding both sections rather than two lists inside a
        // Column - nested scrollables would fight over the available height.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 88.dp, // clear of the floating action button
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            item {
                // No login, so the device id is the whole of "who you are".
                // Visible because it explains why some events are editable.
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

                        // F-13 - the name other people see next to your events.
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

                        // Only offered when there is actually something to save.
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

            // F-25 - runs the reminder check now. Started from a button because
            // Android 12 forbids launching a foreground service from the
            // background; a scheduled trigger needs a different mechanism.
            item {
                OutlinedButton(
                    onClick = {
                        val needsPermission =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !viewModel.canPostNotifications()

                        when {
                            !needsPermission -> EventReminderService.start(context)

                            // After a permanent refusal launch() opens no dialog
                            // and reports nothing, so the button appeared dead.
                            // Settings is the only route left, so say so and
                            // open it rather than pretending to ask again.
                            !canAskForNotifications(context) -> {
                                Toast.makeText(
                                    context, permissionBlockedMessage, Toast.LENGTH_LONG,
                                ).show()
                                context.openAppSettings()
                            }

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

            // ---- events created on this device ----
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

            // ---- private events joined with a code ----
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

            // ---- F-28: people whose events are hidden ----
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
                                // The name if this device ever fetched it,
                                // otherwise a shortened id - still better than
                                // hiding the block itself.
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

/**
 * F-21 - enter the code an organiser shared.
 *
 * A dialog rather than a screen: one short field and one action does not warrant
 * a navigation destination, and staying in place makes it obvious you are still
 * on the account screen when the joined event appears.
 */
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
                    // The keyboard opens in caps because every code is uppercase.
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

/**
 * Whether the system will still show the notification permission dialog.
 *
 * shouldShowRequestPermissionRationale is false both before the first request
 * and after a permanent refusal, so this is only consulted once the permission
 * is known to be missing - at which point false means "asking again does
 * nothing".
 */
private fun canAskForNotifications(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    val activity = context.findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.POST_NOTIFICATIONS,
    )
}
