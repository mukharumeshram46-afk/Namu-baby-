package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraQuickSnapManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraQuickSnap"
    }

    suspend fun takeSnapshot(lifecycleOwner: LifecycleOwner, facingFront: Boolean = false): Result<Bitmap> {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val selector = if (facingFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageCapture)

                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = imageProxyToBitmap(image)
                                image.close()
                                cameraProvider.unbindAll()
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(bitmap))
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "Camera snapshot failed: ${exception.message}", exception)
                                cameraProvider.unbindAll()
                                if (continuation.isActive) {
                                    continuation.resumeWithException(exception)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error configuring camera provider: ${e.message}", e)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate according to camera sensor rotation degrees
        val rotation = image.imageInfo.rotationDegrees
        return if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
}
