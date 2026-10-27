
package com.example.facerecognition.recognition

import android.content.Context
import android.graphics.Bitmap

/**
 * FaceRecognitionManager
 *
 * 組員B：人臉辨識統一管理介面
 *
 * 整合功能：
 * 1. MobileFaceNet 特徵向量提取
 * 2. FaceMatcher 自動人物匹配
 * 3. RecognitionResult 結果輸出
 *
 * 提供組員C呼叫。
 *
 * 輸入條件：
 * 人臉圖片必須已由組員A完成偵測、裁切，
 * 並縮放為 112 × 112 Bitmap。
 *
 * 注意：
 * 目前的 Similarity Threshold = 0.80
 * 只是測試用門檻，尚未完成正式校準。
 */
class FaceRecognitionManager(
    context: Context,
    threshold: Float = 0.80f
) : AutoCloseable {

    // TensorFlow Lite 模型管理器
    private val modelManager = MobileFaceNetManager(
        context.applicationContext
    )

    // 人臉匹配工具
    private val faceMatcher = FaceMatcher(
        recognitionThreshold = FaceRecognitionThreshold(threshold)
    )

    // 記錄模型是否已經關閉
    private var isClosed = false

    /**
     * 提取一張人臉的 128 維特徵向量。
     *
     * 目前 Qualcomm MobileFaceNet 使用雙輸入：
     * img1、img2。
     *
     * 因此將同一張人臉輸入兩次，
     * 並取第一組輸出向量。
     *
     * 此方式已在目前的真人照片測試中
     * 得到初步驗證。
     */
    private fun extractEmbedding(faceBitmap: Bitmap): FloatArray {

        check(!isClosed) {
            "FaceRecognitionManager 已關閉，不能繼續使用"
        }

        require(!faceBitmap.isRecycled) {
            "輸入 Bitmap 已被釋放"
        }

        require(
            faceBitmap.width == 112 &&
                    faceBitmap.height == 112
        ) {
            "人臉圖片必須為 112 × 112"
        }

        // 同一張圖片傳入兩個輸入位置
        val embeddings = modelManager.extractEmbeddings(
            faceBitmap,
            faceBitmap
        )

        // 驗證模型輸出的向量維度
        check(
            embeddings.size == 2 &&
                    embeddings.all { it.size == 128 }
        ) {
            "MobileFaceNet 輸出的特徵向量維度不正確"
        }

        // 回傳第一組 128 維特徵向量
        return embeddings[0].copyOf()
    }

    /**
     * 註冊人臉：產生 128 維特徵向量。
     *
     * @param faceBitmap 已裁切且縮放的 112×112 人臉
     * @return FloatArray(128)
     *
     * 注意：
     * 這裡不會寫入資料庫。
     * 呼叫端需自行保存人物 ID 與特徵向量。
     */
    fun registerFace(faceBitmap: Bitmap): FloatArray {
        return extractEmbedding(faceBitmap)
    }

    /**
     * 辨識人臉。
     *
     * @param faceId 組員A提供的人臉追蹤 ID
     * @param faceBitmap 已裁切的 112×112 人臉
     * @param registeredEmbeddings 已註冊人物特徵資料
     *
     * registeredEmbeddings 格式：
     * Map<String, FloatArray>
     *
     * 例如：
     * "person_a" → 128 維向量
     * "person_b" → 128 維向量
     *
     * @return RecognitionResult
     */
    fun recognizeFace(
        faceId: Int,
        faceBitmap: Bitmap,
        registeredEmbeddings: Map<String, FloatArray>
    ): RecognitionResult {

        // 從待辨識人臉提取特徵向量
        val queryEmbedding = extractEmbedding(faceBitmap)

        // 自動尋找相似度最高的已註冊人物
        return faceMatcher.match(
            faceId = faceId,
            queryEmbedding = queryEmbedding,
            registeredEmbeddings = registeredEmbeddings
        )
    }

    /**
     * 釋放 TensorFlow Lite 模型資源。
     *
     * 同一個 Manager 可以重複進行辨識，
     * 但使用完畢後必須呼叫 close()。
     */
    override fun close() {

        if (!isClosed) {
            isClosed = true
            modelManager.close()
        }
    }
}
