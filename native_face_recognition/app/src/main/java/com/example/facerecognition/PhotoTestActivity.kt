package com.example.facerecognition

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.example.facerecognition.crop.FaceCropper
import com.example.facerecognition.model.DetectedFace
import com.example.facerecognition.quality.FaceQualityChecker

/**
 * PhotoTestActivity
 *
 * 使用本地照片測試人臉偵測
 *
 * 組員A：
 * 用於驗證 MediaPipe、
 * Face Crop 與多人臉功能
 * *功能：
 * 1. MediaPipe Face Detection
 * 2. Multi Face Detection
 * 3. Face Crop
 * 4. Face Quality Checker
 * 5. Blur Detection
 *
 *使用方式：
 * 將測試照片放入 assets
 * 並修改：
 * assets.open("照片檔名放這裡")
 */
class PhotoTestActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        Log.d(
            "FaceRecognition",
            "PhotoTestActivity started"
        )
        try {

            /**
             * 從 assets 讀取測試照片
             */
            val inputStream =
                assets.open(
                    "照片檔名放這裡"
                )

            /**
             * 將照片轉換成 Bitmap
             */
            val bitmap =
                BitmapFactory.decodeStream(
                    inputStream
                )

            /**
             * 輸出照片尺寸
             */
            Log.d(
                "FaceRecognition",
                "Photo loaded: ${bitmap.width} x ${bitmap.height}"
            )

            /**
             * Bitmap 轉 MPImage
             */
            val mpImage =
                BitmapImageBuilder(
                    bitmap
                ).build()

            /**
             * 建立 MediaPipe 偵測器
             */
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(
                        "blaze_face_short_range.tflite"
                    )
                    .build()

            val detectorOptions =
                FaceDetector.FaceDetectorOptions
                    .builder()
                    .setBaseOptions(
                        baseOptions
                    )
                    .setRunningMode(
                        RunningMode.IMAGE
                    )
                    .build()

            val detector =
                FaceDetector.createFromOptions(
                    this,
                    detectorOptions
                )

            /**
             * 執行人臉偵測
             */
            val result =
                detector.detect(
                    mpImage
                )

            Log.d(
                "FaceRecognition",
                "Faces found: ${result.detections().size}"
            )

            /**
             * 輸出每張人臉座標
             */
            result.detections().forEachIndexed {

                    index,
                    detection ->

                val box =
                    detection.boundingBox()

                val detectedFace =
                    DetectedFace(
                        faceId = index,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom
                    )


                /**
                 * 人臉品質檢查
                 */
                val qualityChecker =
                    FaceQualityChecker()

                val isValid =
                    qualityChecker.isFaceValid(
                        detectedFace
                    )

                Log.d(
                    "FaceRecognition",
                    "Quality Pass: $isValid"
                )
                val faceCropper =
                    FaceCropper()
                val croppedFace =
                    faceCropper.cropFace(
                        bitmap,
                        detectedFace
                    )

                if (croppedFace != null) {

                    Log.d(
                        "FaceRecognition",
                        "Crop success: ${croppedFace.width} x ${croppedFace.height}"
                    )

                    /**
                     * 標準化人臉尺寸
                     */
                    val normalizedFace =
                        faceCropper.resizeFace(
                            croppedFace
                        )
                    /**
                     * 模糊度檢查
                     */
                    val isSharp =
                        qualityChecker.isFaceSharpEnough(
                            normalizedFace
                        )

                    Log.d(
                        "FaceRecognition",
                        "Sharp Pass: $isSharp"
                    )

                    Log.d(
                        "FaceRecognition",
                        "Normalized: ${normalizedFace.width} x ${normalizedFace.height}"
                    )

                }
                Log.d(
                    "FaceRecognition",
                    "Face $index"
                )

                Log.d(
                    "FaceRecognition",
                    "Left=${box.left}"
                )

                Log.d(
                    "FaceRecognition",
                    "Top=${box.top}"
                )

                Log.d(
                    "FaceRecognition",
                    "Right=${box.right}"
                )

                Log.d(
                    "FaceRecognition",
                    "Bottom=${box.bottom}"
                )

            }

        } catch (
            exception: Exception
        ) {

            /**
             * 讀取失敗
             */
            Log.e(
                "FaceRecognition",
                exception.toString()
            )

        }

    }

}