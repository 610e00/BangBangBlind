package com.example.facerecognition.detection

import android.content.Context
import android.util.Log
import com.example.facerecognition.model.DetectedFace
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.framework.image.BitmapImageBuilder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MPImage
/**
 * FaceDetectorManager
 *
 * 負責管理人臉偵測功能
 */
class FaceDetectorManager {

    /**
     * MediaPipe 人臉偵測器
     * Day 4 導入
     */
    private var isDetectorReady = false
    private var faceDetector: FaceDetector? = null
    private fun imageProxyToMPImage(
        imageProxy: ImageProxy
    ): MPImage? {

        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )

        return BitmapImageBuilder(bitmap)
            .build()

    }
    /**
     * 偵測器
     */
    fun initializeDetector(
        context: Context
    ) {

        try {

            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(
                        "blaze_face_short_range.tflite"
                    )
                    .build()

            val faceDetectorOptions =
                FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(
                        RunningMode.IMAGE
                    )
                    .build()

            faceDetector =
                FaceDetector.createFromOptions(
                    context,
                    faceDetectorOptions
                )

            isDetectorReady = true

            Log.d(
                "FaceRecognition",
                "MediaPipe initialized"
            )

        } catch (exception: Exception) {

            Log.e(
                "FaceRecognition",
                exception.message ?: "Init error"
            )

        }

    }

    /**
     * 偵測人臉
     */
    fun detectFace(
        imageProxy: androidx.camera.core.ImageProxy
    ): List<DetectedFace>{

        if (!isDetectorReady) {

            Log.e(
                "FaceRecognition",
                "Face detector not initialized"
            )

            return emptyList()

        }

        Log.d(
            "FaceRecognition",
            "Detect face called"
        )
        val mpImage =
            imageProxyToMPImage(
                imageProxy
            )

        if (mpImage == null) {

            return emptyList()

        }

        val detectionResult =
            faceDetector?.detect(
                mpImage
            )

        if (detectionResult == null) {

            return emptyList()

        }

        Log.d(
            "FaceRecognition",
            "Detection success"
        )
        Log.d(
            "FaceRecognition",
            "Face count = ${detectionResult.detections().size}"
        )

        val detectedFaces =
            mutableListOf<DetectedFace>()

        for ((index, detection) in
        detectionResult.detections().withIndex()
        ) {

            val boundingBox =
                detection.boundingBox()

            detectedFaces.add(

                DetectedFace(
                    faceId = index,

                    left = boundingBox.left,

                    top = boundingBox.top,

                    right = boundingBox.right,

                    bottom = boundingBox.bottom
                )

            )

        }

        return detectedFaces

    }



}