package com.example.orbit.ui.screens

import com.example.orbit.domain.model.EventEditRules

import androidx.compose.material.icons.filled.Edit

import androidx.compose.ui.Alignment

import androidx.compose.foundation.layout.Row

import com.example.orbit.ui.components.RatingBar

import androidx.compose.material.icons.filled.FavoriteBorder

import androidx.compose.material.icons.filled.Favorite

import com.example.orbit.ui.common.labelRes

import androidx.compose.ui.res.stringResource

import com.example.orbit.R

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.EmptyView
import com.example.orbit.ui.components.ErrorView
import com.example.orbit.ui.components.LoadingView
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.util.formatEventDateTime

/** F-09 / F-19 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel<EventDetailViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val myRating by viewModel.myRating.collectAsStateWithLifecycle()
    val ratingError by viewModel.ratingError.collectAsStateWithLifecycle()
    val organiser by viewModel.organiser.collectAsStateWithLifecycle()
    val isOrganiserBlocked by viewModel.isOrganiserBlocked.collectAsStateWithLifecycle()

    val event = (uiState as? UiState.Success)?.data
    val isOwner = event != null && event.ownerId == viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.title ?: "Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
                actions = {
                    // Available on any event, yours or not - bookmarking is
                    // about what you want to keep, not what you control.
                    IconButton(onClick = viewModel::toggleSaved) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Favorite
                            else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(
                                if (isSaved) R.string.detail_unsave else R.string.detail_save
                            ),
                        )
                    }

                    // F-09 - owner-only actions. Editing disappears once the
                    // event has started, matching what the server will accept.
                    if (isOwner && event != null &&
                        !EventEditRules.hasStarted(event)
                    ) {
                        IconButton(onClick = { onEdit(event.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.detail_edit),
                            )
                        }
                    }

                    if (isOwner) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(Modifier.padding(innerPadding))
            is UiState.Error -> ErrorView(stringResource(state.messageRes), Modifier.padding(innerPadding))
            is UiState.Success -> {
                val loaded = state.data
                if (loaded == null) {
                    EmptyView(
                        title = stringResource(R.string.detail_not_found_title),
                        subtitle = stringResource(R.string.detail_not_found_subtitle),
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    EventDetailContent(
                        event = loaded,
                        myRating = myRating ?: 0,
                        isOwner = isOwner,
                        organiserName = organiser?.displayName,
                        isOrganiserBlocked = isOrganiserBlocked,
                        onToggleBlock = viewModel::toggleOrganiserBlocked,
                        ratingError = ratingError,
                        onRate = viewModel::submitRating,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_dialog_title)) },
            text = { Text(stringResource(R.string.detail_delete_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                        onBack()
                    },
                ) { Text(stringResource(R.string.detail_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun EventDetailContent(
    event: Event,
    myRating: Int,
    ratingError: Int?,
    onRate: (Int) -> Unit,
    isOwner: Boolean,
    organiserName: String?,
    isOrganiserBlocked: Boolean,
    onToggleBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noMapsAppMessage = stringResource(R.string.detail_no_maps_app)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        if (event.imageUris.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(event.imageUris.size) { index ->
                    AsyncImage(
                        model = event.imageUris[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        Text(event.title, style = MaterialTheme.typography.headlineSmall)
        AssistChip(onClick = { }, label = { Text(stringResource(event.category.labelRes())) })

        Text(formatEventDateTime(event.startTime), style = MaterialTheme.typography.bodyLarge)
        event.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()
        Text(event.description, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()

        event.capacity?.let { Text(stringResource(R.string.detail_capacity, it)) }
        event.price?.let { Text(stringResource(R.string.detail_price, it.toString())) }
        if (event.requiresReservation) Text(stringResource(R.string.detail_reservation_required))
        event.accessCode?.let {
            Text(
                stringResource(R.string.detail_access_code, it),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            stringResource(
                R.string.detail_rating,
                event.avgRating.toString(),
                event.ratingCount,
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        // F-19 - hand the coordinates to whatever maps app the phone has, rather than
        // building routing ourselves. "geo:" is the standard Android maps intent.
        Button(
            onClick = {
                val uri = Uri.parse(
                    "geo:" + event.latitude + "," + event.longitude +
                        "?q=" + event.latitude + "," + event.longitude +
                        "(" + Uri.encode(event.title) + ")"
                )
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, noMapsAppMessage, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.detail_navigate)) }

        HorizontalDivider()

        // F-28 - who made this, and the option to stop seeing their events.
        // Hidden on your own events: blocking yourself would hide your own work.
        if (!isOwner) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.detail_organised_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Falls back to a shortened id when the profile has not
                        // been fetched - offline, or a user the server does not
                        // know about.
                        text = organiserName ?: event.ownerId.take(8),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                TextButton(onClick = onToggleBlock) {
                    Text(
                        stringResource(
                            if (isOrganiserBlocked) R.string.detail_unblock
                            else R.string.detail_block
                        )
                    )
                }
            }

            if (isOrganiserBlocked) {
                Text(
                    text = stringResource(R.string.detail_blocked_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()
        }

        // F-27 - rating unlocks only once the event has started. Judging
        // something that has not happened is meaningless, and the server
        // rejects it too - hiding a control is not the same as forbidding it.
        val hasStarted = event.startTime <= System.currentTimeMillis()

        Text(
            text = stringResource(R.string.rating_your_rating),
            style = MaterialTheme.typography.titleSmall,
        )

        if (hasStarted) {
            RatingBar(rating = myRating, onRatingChange = onRate)
            ratingError?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.rating_not_yet_started),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
