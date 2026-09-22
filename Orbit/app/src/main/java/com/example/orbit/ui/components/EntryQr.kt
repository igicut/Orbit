package com.example.orbit.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.data.image.saveImageToGallery
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.theme.warmShadow
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/** Jedna slika i za ekran i za stampu; na A4 je i dalje ostra */
private const val QR_BITMAP_PX = 1024

/** Na ekranu; telefon gosta ga cita i sa metar udaljenosti */
private val QR_SIZE = 240.dp

/**
 * F-41: tekst u sliku QR koda. zxing racuna mrezu crnih i belih polja (BitMatrix),
 * a ovde se svako polje samo oboji u piksel. Crno na belom, jer to citaju svi skeneri.
 */
fun qrBitmap(text: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        for (x in 0 until sizePx) {
            pixels[y * sizePx + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

/**
 * F-41: QR za ulaz u panelu odozdo, kao spisak prijava. Kod se ne menja,
 * pa sacuvana i odstampana slika vazi do kraja dogadjaja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryQrSheet(
    state: UiState<String>,
    eventId: String,
    eventTitle: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    // Odmah ceo panel, kao filteri; inace dugme za cuvanje ostaje ispod ivice ekrana
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.entry_qr_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.entry_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            when (state) {
                is UiState.Loading -> Box(
                    modifier = Modifier.size(QR_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is UiState.Error -> {
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.common_try_again))
                    }
                }

                is UiState.Success -> {
                    val bitmap = remember(state.data) { qrBitmap(state.data, QR_BITMAP_PX) }

                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.entry_qr_description, eventTitle),
                        // Bez zamucivanja pri smanjivanju; mutne ivice polja skener teze cita
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(QR_SIZE)
                            .warmShadow(elevation = 4.dp, shape = MaterialTheme.shapes.large)
                            .clip(MaterialTheme.shapes.large),
                    )

                    Text(
                        text = eventTitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )

                    // Cuvanje bez dozvole postoji tek od Androida 10; ranije se QR moze samo pokazati
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    val saved = saveImageToGallery(context, bitmap, "orbit_qr_$eventId.png")
                                    isSaving = false
                                    val message = if (saved) R.string.entry_qr_saved else R.string.entry_qr_save_failed
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.entry_qr_save))
                        }
                    }
                }
            }
        }
    }
}
