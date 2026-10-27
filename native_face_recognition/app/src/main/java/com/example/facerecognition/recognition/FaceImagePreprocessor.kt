package com.example.facerecognition.recognition

import android.graphics.Bitmap
import android.graphics.Color

/**
 * FaceImagePreprocessor
 *
 * 組員B：人臉圖片前處理
 *
 * 功能：
 * 1. 接收組員A輸出的 Bitmap
 * 2. 確認圖片尺寸為 112 × 112
 * 3. 將 RGB 像素轉換成 Float32
 * 4. 將像素範圍從 0~255 轉換為 0~1
 * 5. 依照模型規格輸出 NCHW 格式
 *
 * MobileFaceNet 輸入規格：
 * [1, 3, 112, 112]
 *
 * 注意：
 * 此類別只負責格式轉換，
 * 不執行人臉偵測或特徵提取。
 */
class FaceImagePreprocessor {

    /**
     * 將 112 × 112 Bitmap 轉換為 MobileFaceNet 輸入資料。
     *
     * 輸出排列：
     * [Batch][Channel][Height][Width]
     *
     * Channel 0 = Red
     * Channel 1 = Green
     * Channel 2 = Blue
     */
    fun bitmapToFloatArray(
        faceBitmap: Bitmap
    ): Array<Array<Array<FloatArray>>> {

        // 確認圖片尺寸符合模型要求
        require(
            faceBitmap.width == 112 &&
                    faceBitmap.height == 112
        ) {
            "人臉圖片尺寸必須為 112x112"
        }

        // 建立 [1, 3, 112, 112] 的四維 Float 陣列
        val inputArray = Array(1) {
            Array(3) {
                Array(112) {
                    FloatArray(112)
                }
            }
        }

        // 逐一讀取圖片的每個像素
        for (y in 0 until 112) {
            for (x in 0 until 112) {

                val pixel = faceBitmap.getPixel(x, y)

                // 將 RGB 轉為 0~1 的 Float32
                val red = Color.red(pixel) / 255.0f
                val green = Color.green(pixel) / 255.0f
                val blue = Color.blue(pixel) / 255.0f

                // 依照 NCHW 格式放入對應通道
                inputArray[0][0][y][x] = red
                inputArray[0][1][y][x] = green
                inputArray[0][2][y][x] = blue
            }
        }

        return inputArray
    }
}
