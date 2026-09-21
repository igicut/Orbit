package com.example.orbit.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.example.orbit.domain.model.Visibility
import com.example.orbit.domain.model.organiserRating
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.RatingStatTile
import com.example.orbit.ui.components.StatTile
import com.example.orbit.ui.components.UserHeaderCard
import com.example.orbit.ui.components.findActivity
import com.example.orbit.ui.components.openAppSettings
import com.example.orbit.ui.stateholders.AccountViewModel
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow

/** Krug iza ikonice u boji stavke, kao na detalju dogadjaja */
private const val ICON_CIRCLE_ALPHA = 0.15f

/** Nalog: zaglavlje sa brojevima, pa kartice koje vode na svoje liste */
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
    val attendedCount by viewModel.attendedCount.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showNameSheet by remember { mutableStateOf(false) }

    val myEvents = (uiState as? UiState.Success)?.data.orEmpty()

    // Ocena kakvu vide drugi: profil organizatora racuna samo javne dogadjaje
    val rating = organiserRating(myEvents.filter { it.visibility == Visibility.PUBLIC })

    // Bez gornje trake: naslov "Account" je ponavljao naziv taba i trosio visinu
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Dno nosi visinu plutajuce trake, jer sadrzaj ide ispod nje
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + orbitBottomBarSpace),
    ) {

        UserHeaderCard(
            name = savedDisplayName,
            subtitle = viewModel.email,
            onEdit = { showNameSheet = true },
        ) {
            RatingStatTile(rating = rating, modifier = Modifier.weight(1f))
            StatTile(
                value = myEvents.size.toString(),
                label = stringResource(R.string.stat_organised),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = attendedCount.toString(),
                label = stringResource(R.string.stat_attended),
                modifier = Modifier.weight(1f),
            )
        }

        // Dvostruki razmak: zaglavlje i meni nisu ista celina
        Spacer(modifier = Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuCard(
                icon = Icons.Filled.DateRange,
                color = MaterialTheme.orbitAccents.brandStart,
                label = stringResource(R.string.account_my_events),
                count = myEvents.size,
                onClick = onMyEventsClick,
            )
            MenuCard(
                icon = Icons.Filled.Lock,
                color = MaterialTheme.orbitAccents.brandEnd,
                label = stringResource(R.string.account_joined_events),
                count = joinedEvents.size,
                onClick = onJoinedEventsClick,
            )
            MenuCard(
                icon = Icons.Filled.Person,
                color = MaterialTheme.orbitAccents.noSpots,
                label = stringResource(R.string.account_blocked_users),
                count = blockedUsers.size,
                onClick = onBlockedUsersClick,
            )
        }

        // Odjava je odvojena od ostalih, da se ne pritisne slucajno
        Spacer(modifier = Modifier.height(24.dp))

        MenuCard(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            color = MaterialTheme.orbitAccents.noSpots,
            label = stringResource(R.string.account_logout),
            count = null,
            onClick = { showLogoutDialog = true },
        )

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

/** Stavka menija kao kartica: ikonica u obojenom krugu, naziv, broj i strelica */
@Composable
private fun MenuCard(
    icon: ImageVector,
    color: Color,
    label: String,
    /** null kad stavka nema broj, npr. odjava */
    count: Int?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = color.copy(alpha = ICON_CIRCLE_ALPHA), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )

            count?.let {
                // Pilula u boji stavke; broj ostaje u boji teksta, zbog kontrasta
                Surface(shape = CircleShape, color = color.copy(alpha = ICON_CIRCLE_ALPHA)) {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
