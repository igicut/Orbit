package com.example.orbit.ui.components

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.orbit.R
import com.example.orbit.data.camera.CameraSession
import com.example.orbit.data.camera.startCamera
import com.example.orbit.data.camera.takePicture

/**
 * Full-screen viewfinder with a shutter button.
 *
 * PreviewView is an Android View, so it is hosted through AndroidView - the same
 * arrangement as the osmdroid map.
 */
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

    // Start on entry, and release on exit. The release half matters: CameraX
    // binds to the ACTIVITY lifecycle, so leaving this composable would
    // otherwise leave the camera running.
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

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.camera_close),
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            val shutterLabel = stringResource(R.string.camera_shutter)

            FloatingActionButton(
                onClick = {
                    // Null until the provider has finished starting up, so an
                    // early tap is simply ignored rather than crashing.
                    val active = session ?: return@FloatingActionButton
                    takePicture(
                        context = context,
                        imageCapture = active.imageCapture,
                        lensFacing = active.lensFacing,
                        onSaved = onPhotoTaken,
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = shutterLabel },
            ) {
                // A plain white disc: material-icons-core has no camera glyph,
                // and a shutter reads as a circle anyway.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}
