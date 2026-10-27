
package com.example.facerecognition.recognition

/**
 * FaceRecognitionThreshold
 *
 * 組員B：人臉辨識相似度門檻判斷
 *
 * 功能：
 * 1. 接收 Cosine Similarity 分數
 * 2. 根據設定的門檻判斷是否通過
 *
 * 注意：
 * 目前 0.80 僅為初步測試門檻，
 * 不是經過大量資料驗證的正式門檻。
 *
 * 後續需要使用更多人物與照片，
 * 評估誤認率（FAR）與拒認率（FRR）。
 */
class FaceRecognitionThreshold(
    private val threshold: Float = 0.80f
) {

    init {
        // 確保門檻落在 Cosine Similarity 的合理範圍
        require(threshold in -1.0f..1.0f) {
            "Similarity Threshold 必須介於 -1 與 1 之間"
        }
    }

    /**
     * 根據相似度判斷是否達到辨識門檻。
     *
     * @param similarity 兩組特徵向量的餘弦相似度
     * @return true = 通過門檻；false = 未通過
     */
    fun isMatch(similarity: Float): Boolean {

        // 拒絕 NaN 或 Infinity 等無效輸入
        require(similarity.isFinite()) {
            "相似度不可為 NaN 或 Infinity"
        }

        require(similarity in -1.0f..1.0f) {
            "相似度必須介於 -1 與 1 之間"
        }

        return similarity >= threshold
    }

    /**
     * 提供外部程式查詢目前使用的門檻。
     */
    fun getThreshold(): Float {
        return threshold
    }
}
