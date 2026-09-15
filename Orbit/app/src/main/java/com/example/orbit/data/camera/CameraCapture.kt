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

// Koristi ga createFile() iz materijala, a nije definisan
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val PHOTO_EXTENSION = ".jpg"

/** Privatni folder aplikacije, ne treba dozvola za skladiste */
fun getOutputMediaDirectory(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir

fun createFile(baseFolder: File, format: String, extension: String): File =
    File(
        baseFolder,
        SimpleDateFormat(format, Locale.US).format(System.currentTimeMillis()) + extension,
    )

/** Ispravlja preslikanu sliku sa prednje kamere */
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

/** Pokrenuta kamera, za slikanje i kasnije oslobadjanje */
class CameraSession(
    val imageCapture: ImageCapture,
    val lensFacing: Int,
    private val cameraProvider: ProcessCameraProvider,
) {
    /** Bez unbind kamera ostaje otvorena u pozadini */
    fun release() = cameraProvider.unbindAll()
}

/** Slajdovi 11 i 13, spojeni i dopunjeni */
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
            // Umesto izuzetka javljamo gresku, ostaje galerija
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

/** Slajd 15 + onSaved vraca URI slike */
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
