package com.example.orbit.ui.components

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.orbit.R
import com.example.orbit.data.camera.CameraSession
import com.example.orbit.data.camera.startCamera
import com.example.orbit.data.camera.takePicture

/** Okidac je velik, jer se pritiska palcem dok se gleda slika */
private val SHUTTER_SIZE = 80.dp

/** Krug iza dugmeta za zatvaranje */
private const val CONTROL_SCRIM_ALPHA = 0.4f

/** Kamera preko celog ekrana sa dugmetom za slikanje */
@Composable
fun CameraCaptureView(
    onPhotoTaken: (Uri) -> Unit,
    onCancel: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var session by remember { mutableStateOf<CameraSession?>(null) }

    // Pokreni pri ulasku, oslobodi pri izlasku
    DisposableEffect(Unit) {
        startCamera(
            context = context,
            previewView = previewView,
            lifecycleOwner = lifecycleOwner,
            onReady = { session = it },
            onUnavailable = onUnavailable,
        )
        onDispose {
            session?.release()
            session = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Tamni krug, da se ✕ vidi i preko svetle slike
        IconButton(
            onClick = onCancel,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA),
                contentColor = Color.White,
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.camera_close))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            val shutterLabel = stringResource(R.string.camera_shutter)

            // Klasican okidac: beli prsten i beli krug u njemu
            Box(
                modifier = Modifier
                    .size(SHUTTER_SIZE)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        // null dok se kamera ne pokrene, rani klik se ignorise
                        val active = session ?: return@clickable
                        takePicture(
                            context = context,
                            imageCapture = active.imageCapture,
                            lensFacing = active.lensFacing,
                            onSaved = onPhotoTaken,
                        )
                    }
                    .semantics { contentDescription = shutterLabel }
                    .border(width = 4.dp, color = Color.White, shape = CircleShape)
                    .padding(8.dp)
                    .background(color = Color.White, shape = CircleShape),
            )
        }
    }
}
