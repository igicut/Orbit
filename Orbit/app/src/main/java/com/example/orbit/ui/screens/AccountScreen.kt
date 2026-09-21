package com.example.orbit.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.BuildConfig
import com.example.orbit.R
import com.example.orbit.ui.navigation.orbitBottomBarSpace
import com.example.orbit.data.notification.EventReminderService
import com.example.orbit.data.notification.REMINDER_WINDOW_HOURS
import com.example.orbit.data.notification.ReminderOutcome
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.findActivity
import com.example.orbit.ui.components.openAppSettings
import com.example.orbit.ui.stateholders.AccountViewModel
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow

private val AVATAR_SIZE = 48.dp

/** Nalog: profil, pa redovi koji vode na svoje liste */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onMyEventsClick: () -> Unit,
    onJoinedEventsClick: () -> Unit,
    onBlockedUsersClick: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val joinedEvents by viewModel.joinedEvents.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val savedDisplayName by viewModel.savedDisplayName.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showNameSheet by remember { mutableStateOf(false) }

    val myEventsCount = (uiState as? UiState.Success)?.data?.size ?: 0

    // Bez gornje trake: naslov "Account" je ponavljao naziv taba i trosio visinu
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Dno nosi visinu plutajuce trake, jer sadrzaj ide ispod nje
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + orbitBottomBarSpace),
    ) {

        ProfileRow(
            displayName = savedDisplayName,
            email = viewModel.email,
            onClick = { showNameSheet = true },
        )

        // Dvostruki razmak: profil i meni nisu ista celina
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium),
        ) {
            MenuRow(
                icon = Icons.Filled.DateRange,
                label = stringResource(R.string.account_my_events),
                count = myEventsCount,
                onClick = onMyEventsClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(
                icon = Icons.Filled.Lock,
                label = stringResource(R.string.account_joined_events),
                count = joinedEvents.size,
                onClick = onJoinedEventsClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MenuRow(
                icon = Icons.Filled.Person,
                iconTint = MaterialTheme.orbitAccents.noSpots,
                label = stringResource(R.string.account_blocked_users),
                count = blockedUsers.size,
                onClick = onBlockedUsersClick,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium),
        ) {
            MenuRow(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                iconTint = MaterialTheme.orbitAccents.noSpots,
                label = stringResource(R.string.account_logout),
                count = null,
                onClick = { showLogoutDialog = true },
            )
        }

        // Rucna provera podsetnika je alat za proveru, ne za korisnike
        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(24.dp))
            DebugReminderCheck(viewModel)
        }
    }

    if (showNameSheet) {
        DisplayNameSheet(
            viewModel = viewModel,
            onDismiss = { showNameSheet = false },
        )
    }

    // Upozorenje, dogadjaji napravljeni bez mreze postoje samo lokalno
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_dialog_title)) },
            text = { Text(stringResource(R.string.logout_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logOut()
                    },
                ) {
                    Text(stringResource(R.string.account_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Ime, email i pocetno slovo; ceo red otvara izmenu imena */
@Composable
private fun ProfileRow(
    displayName: String,
    email: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(AVATAR_SIZE)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.account_edit_name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Red menija: ikonica, naziv, broj i strelica */
@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    /** null kad red nema broj, npr. odjava */
    count: Int?,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // 56 dp visine, iznad minimalne mete od 44 dp
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** F-13: ime za prikaz se menja retko, zato panel a ne stalno polje */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayNameSheet(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.account_edit_name),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringResource(R.string.account_display_name)) },
                singleLine = true,
                isError = nameError != null,
                supportingText = {
                    Text(stringResource(nameError ?: R.string.account_display_name_hint))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    viewModel.saveDisplayName()
                    onDismiss()
                },
                enabled = displayName.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.account_save_name))
            }
        }
    }
}

/**
 * F-25: rucna provera podsetnika; samo u debug verziji.
 * Trazi dozvolu za obavestenja jer je servis pokrece odmah.
 */
@Composable
private fun DebugReminderCheck(viewModel: AccountViewModel) {
    val context = LocalContext.current

    val permissionDeniedMessage = stringResource(R.string.reminder_permission_denied)
    val permissionBlockedMessage = stringResource(R.string.reminder_permission_blocked)

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

    var reminderOutcome by remember { mutableStateOf<ReminderOutcome?>(null) }

    LaunchedEffect(Unit) {
        viewModel.reminderOutcome.collect { reminderOutcome = it }
    }

    val reminderMessage = when (val outcome = reminderOutcome) {
        null -> null
        is ReminderOutcome.Posted ->
            pluralStringResource(R.plurals.reminder_posted, outcome.count, outcome.count)

        ReminderOutcome.AlreadyNotified -> stringResource(R.string.reminder_already_notified)
        ReminderOutcome.NothingSoon ->
            stringResource(R.string.reminder_nothing_soon, REMINDER_WINDOW_HOURS)

        ReminderOutcome.NoRegistrations -> stringResource(R.string.reminder_no_registrations)
        // Bez sesije se ovaj ekran i ne vidi
        ReminderOutcome.NoSession -> null
        ReminderOutcome.PermissionMissing -> permissionDeniedMessage
        ReminderOutcome.Failed -> stringResource(R.string.reminder_failed)
    }

    LaunchedEffect(reminderMessage) {
        reminderMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            reminderOutcome = null
        }
    }

    OutlinedButton(
        onClick = {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !viewModel.canPostNotifications()

            if (needsPermission) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                EventReminderService.start(context)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_check_reminders))
    }
}

/** F-21: dijalog za unos pristupnog koda */
@Composable
internal fun JoinPrivateEventDialog(viewModel: AccountViewModel) {
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
