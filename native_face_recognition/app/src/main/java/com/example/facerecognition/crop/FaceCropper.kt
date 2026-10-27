package com.example.facerecognition.crop

import android.graphics.Bitmap
import com.example.facerecognition.model.DetectedFace

/**
 * FaceCropper
 *
 * 負責裁切人臉區域
 *
 * 組員A：
 * - 根據人臉座標裁切影像
 * - 輸出標準化人臉圖片
 *
 * 組員B：
 * - 接收 croppedFace
 * - 提取特徵向量
 */
class FaceCropper {

    /**
     * 裁切人臉
     *
     * Day 5 實作
     */
    fun cropFace(
        originalBitmap: Bitmap,
        detectedFace: DetectedFace
    ): Bitmap? {

        try {

            val left =
                detectedFace.left.toInt().coerceAtLeast(0)

            val top =
                detectedFace.top.toInt().coerceAtLeast(0)

            val right =
                detectedFace.right.toInt()
                    .coerceAtMost(originalBitmap.width)

            val bottom =
                detectedFace.bottom.toInt()
                    .coerceAtMost(originalBitmap.height)

            val width = right - left
            val height = bottom - top

            if (
                width <= 0 ||
                height <= 0
            ) {
                return null
            }

            return Bitmap.createBitmap(
                originalBitmap,
                left,
                top,
                width,
                height
            )

        } catch (exception: Exception) {

            exception.printStackTrace()

            return null

        }

    }
    /**
     * 標準化人臉尺寸
     */
    fun resizeFace(
        croppedFace: Bitmap,
        targetSize: Int = 112
    ): Bitmap {

        return Bitmap.createScaledBitmap(
            croppedFace,
            targetSize,
            targetSize,
            true
        )

    }

}