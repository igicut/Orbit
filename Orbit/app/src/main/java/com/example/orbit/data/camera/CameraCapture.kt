package com.example.orbit.data.camera

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "OrbitCamera"

// Referenced by the material's createFile() but never defined there.
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val PHOTO_EXTENSION = ".jpg"

/**
 * Where captured photos are written.
 *
 * The app's own external files directory: it needs no storage permission on any
 * API level, and Android removes it when the app is uninstalled. Photos taken
 * for an event are not scattered into the user's gallery.
 *
 * This is why the material's updateMedia() call is not reproduced - it exists to
 * announce a file to the media scanner, and an app-private directory is not
 * indexed by it anyway.
 */
fun getOutputMediaDirectory(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir

fun createFile(baseFolder: File, format: String, extension: String): File =
    File(
        baseFolder,
        SimpleDateFormat(format, Locale.US).format(System.currentTimeMillis()) + extension,
    )

/**
 * Front cameras produce a mirrored image; isReversedHorizontal tells CameraX to
 * flip it back so a photo looks the way the subject did, not the preview.
 */
fun getOutputFileOptions(lensFacing: Int?, photoFile: File): ImageCapture.OutputFileOptions {
    val metadata = ImageCapture.Metadata().apply {
        isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
    }
    return ImageCapture.OutputFileOptions.Builder(photoFile)
        .setMetadata(metadata)
        .build()
}

private fun hasBackCamera(cameraProvider: ProcessCameraProvider): Boolean =
    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

private fun hasFrontCamera(cameraProvider: ProcessCameraProvider): Boolean =
    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

/** What a started camera hands back, so the screen can shoot and later release. */
class CameraSession(
    val imageCapture: ImageCapture,
    val lensFacing: Int,
    private val cameraProvider: ProcessCameraProvider,
) {
    /**
     * Not in the material, but necessary: bindToLifecycle ties the camera to the
     * ACTIVITY lifecycle, so leaving this screen does not release it. Without an
     * explicit unbind the camera stays open in the background.
     */
    fun release() = cameraProvider.unbindAll()
}

/**
 * Slides 11 and 13, combined and with the elided parts filled in.
 *
 * getApplication() in the material becomes a Context parameter - the original
 * lives in an AndroidViewModel, and this does not.
 *
 * onReady replaces the material's field assignment: the provider arrives on a
 * listener, so the result cannot simply be returned.
 */
fun startCamera(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    onReady: (CameraSession) -> Unit,
    onUnavailable: () -> Unit,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build()

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val lensFacing = when {
            hasBackCamera(cameraProvider) -> CameraSelector.LENS_FACING_BACK
            hasFrontCamera(cameraProvider) -> CameraSelector.LENS_FACING_FRONT
            // The material throws IllegalStateException here. Crashing because a
            // device has no camera is not acceptable when the gallery picker is
            // still a perfectly good alternative, so this reports instead.
            else -> {
                onUnavailable()
                return@addListener
            }
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
        preview.surfaceProvider = previewView.surfaceProvider

        onReady(CameraSession(imageCapture, lensFacing, cameraProvider))
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Slide 15, with one addition: the material only logs the result, so the caller
 * had no way to learn where the photo went. onSaved reports the URI back.
 */
fun takePicture(
    context: Context,
    imageCapture: ImageCapture,
    lensFacing: Int? = null,
    onSaved: (Uri) -> Unit = {},
    onError: () -> Unit = {},
) {
    val outputDirectory = getOutputMediaDirectory(context)
    val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
    val outputFileOptions = getOutputFileOptions(lensFacing, photoFile)
    val mainExecutor = ContextCompat.getMainExecutor(context)

    imageCapture.takePicture(
        outputFileOptions,
        mainExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Log.d(TAG, "Photo capture succeeded: " + Uri.fromFile(photoFile))
                onSaved(output.savedUri ?: Uri.fromFile(photoFile))
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                onError()
            }
        },
    )
}
