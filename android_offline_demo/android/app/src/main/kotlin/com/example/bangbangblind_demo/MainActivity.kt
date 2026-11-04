package com.example.bangbangblind_demo

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread

class MainActivity : FlutterActivity() {

    private val channelName = "bangbangblind/digit_cnn"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // BEGIN SEGMENTER V4 METHOD CHANNEL
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "bangbangblind/countdown_segmenter"
        ).setMethodCallHandler { call, result ->
            when (call.method) {

                "segmentCountdownNoOverlap" -> {
                    val imageBytes = call.arguments as? ByteArray

                    if (imageBytes == null ||
                        imageBytes.isEmpty() ||
                        imageBytes.size > 10 * 1024 * 1024
                    ) {
                        result.error(
                            "INVALID_IMAGE",
                            "需要有效且不超過 10 MB 的 ROI 圖片",
                            null
                        )
                    } else {
                        thread(name = "segmenter-no-overlap") {
                            try {
                                val segmentation =
                                    CountdownNoOverlapTest()
                                        .segment(imageBytes)

                                val response = mapOf(
                                    "digit_count" to
                                        segmentation.digitCount,
                                    "strategy" to
                                        segmentation.strategy,
                                    "boxes_xyxy" to
                                        segmentation.boxesXyxy,
                                    "foreground_aspect_ratio" to
                                        segmentation.foregroundAspectRatio,
                                    "crops_png" to
                                        segmentation.cropsPng
                                )

                                runOnUiThread {
                                    result.success(response)
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error(
                                        "NO_OVERLAP_ERROR",
                                        e.message ?: e.toString(),
                                        null
                                    )
                                }
                            }
                        }
                    }
                }

                "segmentCountdown" -> {
                    val imageBytes = call.arguments as? ByteArray

                    if (imageBytes == null ||
                        imageBytes.isEmpty() ||
                        imageBytes.size > 10 * 1024 * 1024
                    ) {
                        result.error(
                            "INVALID_IMAGE",
                            "需要有效且不超過 10 MB 的 ROI 圖片",
                            null
                        )
                    } else {
                        thread(name = "countdown-segmenter") {
                            try {
                                val segmentation =
                                    CountdownSegmenterV4()
                                        .segment(imageBytes)

                                val response = mapOf(
                                    "digit_count" to
                                        segmentation.digitCount,
                                    "strategy" to
                                        segmentation.strategy,
                                    "boxes_xyxy" to
                                        segmentation.boxesXyxy,
                                    "foreground_aspect_ratio" to
                                        segmentation.foregroundAspectRatio,
                                    "crops_png" to
                                        segmentation.cropsPng
                                )

                                runOnUiThread {
                                    result.success(response)
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error(
                                        "SEGMENTER_ERROR",
                                        e.message ?: e.toString(),
                                        null
                                    )
                                }
                            }
                        }
                    }
                }

                else -> result.notImplemented()
            }
        }
        // END SEGMENTER V4 METHOD CHANNEL



        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            channelName
        ).setMethodCallHandler { call, result ->

            when (call.method) {


                "predictDigitOpenCvFp16" -> {
                    val imageBytes = call.arguments as? ByteArray

                    if (
                        imageBytes == null ||
                        imageBytes.isEmpty() ||
                        imageBytes.size > 10 * 1024 * 1024
                    ) {
                        result.error(
                            "INVALID_IMAGE",
                            "需要有效且不超過 10 MB 的數字圖片",
                            null
                        )
                    } else {
                        thread(name = "digit-cnn-fp16") {
                            try {
                                val tensor = DigitD5OpenCv()
                                    .preprocess(imageBytes)

                                val logits = DigitCnnFp16Native(
                                    applicationContext
                                ).predict(tensor)

                                runOnUiThread {
                                    result.success(logits.toList())
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error(
                                        "CNN_FP16_ERROR",
                                        e.message ?: e.toString(),
                                        null
                                    )
                                }
                            }
                        }
                    }
                }

                "predictDigitOpenCv" -> {
                    val imageBytes = call.arguments as? ByteArray

                    if (imageBytes == null ||
                        imageBytes.isEmpty() ||
                        imageBytes.size > 10 * 1024 * 1024
                    ) {
                        result.error(
                            "INVALID_IMAGE",
                            "需要有效且不超過 10 MB 的數字 Crop",
                            null
                        )
                    } else {
                        thread(name = "digit-cnn-opencv-d5") {
                            try {
                                val tensor = DigitD5OpenCv()
                                    .preprocess(imageBytes)

                                val logits = DigitCnnNative(
                                    applicationContext
                                ).predict(tensor)

                                runOnUiThread {
                                    result.success(logits.toList())
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error(
                                        "CNN_OPENCV_D5_ERROR",
                                        e.message ?: e.toString(),
                                        null
                                    )
                                }
                            }
                        }
                    }
                }

                "predictDigit" -> {
                    val rawInput = call.arguments as? List<*>

                    if (rawInput == null ||
                        rawInput.size != 12288 ||
                        rawInput.any { it !is Number }
                    ) {
                        result.error(
                            "INVALID_INPUT",
                            "CNN 需要 12288 個數值",
                            null
                        )
                    } else {
                        val inputData = FloatArray(12288) { i ->
                            (rawInput[i] as Number).toFloat()
                        }

                        thread(name = "digit-cnn-predict") {
                            try {
                                val logits = DigitCnnNative(
                                    applicationContext
                                ).predict(inputData)

                                runOnUiThread {
                                    result.success(logits.toList())
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    result.error(
                                        "CNN_NATIVE_ERROR",
                                        e.message ?: e.toString(),
                                        null
                                    )
                                }
                            }
                        }
                    }
                }

                "testZeroInput" -> {
                    // Run native inference off the UI thread.
                    thread(name = "digit-cnn-test") {
                        try {
                            val logits = DigitCnnNative(
                                applicationContext
                            ).testZeroInput()

                            runOnUiThread {
                                result.success(logits.toList())
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                result.error(
                                    "CNN_NATIVE_ERROR",
                                    e.message ?: e.toString(),
                                    null
                                )
                            }
                        }
                    }
                }

                else -> result.notImplemented()
            }
        }
    }
}
