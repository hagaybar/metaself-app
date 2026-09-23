package com.metaself.app.ui.screen.scan

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * The camera, reading barcodes.
 *
 * Nothing here can be tested on the build machine: there is no emulator and no camera. It is
 * therefore kept as thin as it can be made — open the camera, hand each frame to the reader, pass
 * the first number found upwards, and stop. Every decision about what that number MEANS is made in
 * the view model, where it can be tested.
 *
 * Only the linear formats a food package actually carries are asked for. Narrowing the set makes the
 * reader faster and stops a QR code on the back of a packet being read as though it were the
 * product.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeCamera(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build(),
        )
    }

    // The executor and the reader both hold threads. Left running, they outlive the screen.
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)

            providerFuture.addListener(
                {
                    val provider = providerFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        // Frames arrive faster than they can be read. Keeping only the newest means
                        // the reader is always looking at what the camera sees now, rather than
                        // working through a backlog of what it saw a second ago.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { imageProxy ->
                        val image = imageProxy.image
                        if (image == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        scanner.process(
                            InputImage.fromMediaImage(
                                image,
                                imageProxy.imageInfo.rotationDegrees,
                            ),
                        )
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcode)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }

                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }
                },
                ContextCompat.getMainExecutor(viewContext),
            )

            previewView
        },
    )
}
