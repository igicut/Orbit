package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls

/** Isti zaobljeni oblik kao cipovi filtera */
private val PHOTO_SHAPE = RoundedCornerShape(16.dp)

/** Pozadina pregleda; tamna da slika bude u prvom planu, ali se vidi da je iznad ekrana */
private const val VIEWER_SCRIM_ALPHA = 0.92f

/** Brojac preko fotografije; skoro bela pilula kao bedzevi na kartici */
private const val COUNTER_ALPHA = 0.85f

/**
 * Fotografije dogadjaja na vrhu detalja, jedna po jedna prevlacenjem.
 * Brojac "1/3" kaze da ima jos; dodir otvara celu, neisecenu fotografiju.
 */
@Composable
fun EventPhotoPager(paths: List<String>, grayscale: Boolean, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { paths.size })
    var opened by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = ImageUrls.model(paths[page]),
                contentDescription = stringResource(R.string.photo_open),
                contentScale = ContentScale.Crop,
                colorFilter = if (grayscale) GRAYSCALE_FILTER else null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { opened = paths[page] },
            )
        }

        if (paths.size > 1) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = COUNTER_ALPHA),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.photo_counter, pagerState.currentPage + 1, paths.size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }

    opened?.let { PhotoViewerDialog(path = it, onDismiss = { opened = null }) }
}

/** Isecena plocica; pozadina drzi mesto dok slika ne stigne */
@Composable
fun PhotoTile(path: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageUrls.model(path),
        contentDescription = stringResource(R.string.photo_open),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(PHOTO_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

/**
 * Cela fotografija, bez secenja. Obican Dialog umesto novog ekrana: nema rute u navigaciji
 * i nema biblioteke za galeriju. Dodir bilo gde zatvara.
 */
@Composable
fun PhotoViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = VIEWER_SCRIM_ALPHA))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageUrls.model(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.photo_close),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}
