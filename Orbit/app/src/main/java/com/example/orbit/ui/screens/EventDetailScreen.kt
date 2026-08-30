package com.example.orbit.ui.screens

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
    viewModel: EventDetailViewModel = hiltViewModel<EventDetailViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    val event = (uiState as? UiState.Success)?.data
    val isOwner = event != null && event.ownerId == viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.title ?: "Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // F-09 - owner-only actions.
                    if (isOwner) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete event")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->

        when (val state = uiState) {
            is UiState.Loading -> LoadingView(Modifier.padding(innerPadding))
            is UiState.Error -> ErrorView(state.message, Modifier.padding(innerPadding))
            is UiState.Success -> {
                val loaded = state.data
                if (loaded == null) {
                    EmptyView(
                        title = "Event not found",
                        subtitle = "It may have been deleted.",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    EventDetailContent(
                        event = loaded,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete event?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                        onBack()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EventDetailContent(
    event: Event,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
        AssistChip(onClick = { }, label = { Text(event.category.label) })

        Text(formatEventDateTime(event.startTime), style = MaterialTheme.typography.bodyLarge)
        event.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()
        Text(event.description, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()

        event.capacity?.let { Text("Capacity: " + it) }
        event.price?.let { Text("Price: " + it) }
        if (event.requiresReservation) Text("Reservation required")
        event.accessCode?.let {
            Text("Access code: " + it, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Rating: " + event.avgRating + " (" + event.ratingCount + ")",
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
                    Toast.makeText(context, "No maps app installed", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Navigate") }

        // TODO(F-27): 5-star rating input goes here.
        // TODO(F-28): "block this owner" action goes here.
    }
}
