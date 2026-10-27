package com.example.facerecognition.quality

import com.example.facerecognition.model.DetectedFace
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
/**
 * FaceQualityChecker
 *
 * 檢查人臉品質
 *
 * 組員A：
 * - 人臉大小檢查
 * - 人臉完整度檢查
 * - 模糊度檢查
 *
 * 組員B：
 * 只接收品質合格的人臉
 */
class FaceQualityChecker {

    /**
     * 檢查人臉是否符合辨識條件
     *
     * Day 6 實作
     */
    fun isFaceValid(
        detectedFace: DetectedFace
    ): Boolean {

        val faceWidth =
            detectedFace.right - detectedFace.left

        val faceHeight =
            detectedFace.bottom - detectedFace.top

        // 人臉太小直接淘汰
        if (
            faceWidth < 80f ||
            faceHeight < 80f
        ) {
            return false
        }

        return true

    }
    /**
     * 模糊度檢查
     *
     * 使用簡化版 Laplacian 概念
     * 計算相鄰像素亮度差異
     */
    fun isFaceSharpEnough(
        faceBitmap: Bitmap
    ): Boolean {

        var edgeScore = 0L

        for (
        x in 1 until faceBitmap.width - 1
        ) {

            for (
            y in 1 until faceBitmap.height - 1
            ) {

                val center =
                    Color.red(
                        faceBitmap.getPixel(x, y)
                    )

                val right =
                    Color.red(
                        faceBitmap.getPixel(x + 1, y)
                    )

                edgeScore +=
                    kotlin.math.abs(
                        center - right
                    )

            }

        }

        val averageScore =
            edgeScore.toDouble() /
                    (
                            faceBitmap.width *
                                    faceBitmap.height
                            )
        Log.d(
            "FaceRecognition",
            "Sharp Score: $averageScore"
        )
        return averageScore > 5.0


    }

}