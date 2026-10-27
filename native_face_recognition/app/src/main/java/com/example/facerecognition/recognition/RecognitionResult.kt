
package com.example.facerecognition.recognition

/**
 * RecognitionResult
 *
 * 組員B：人臉辨識結果資料格式
 *
 * 提供組員C與其他系統模組使用。
 *
 * @property faceId 人臉追蹤編號，由組員A的追蹤功能提供
 *
 * @property personId
 * 已匹配人物的識別編號。
 * 未匹配時為 null。
 *
 * @property similarity
 * 人臉特徵向量的 Cosine Similarity。
 * 範圍為 -1.0 至 1.0。
 *
 * @property isRecognized
 * 是否達到設定的辨識門檻。
 *
 * 注意：
 * 是否通過門檻不等於身分已被完全確認。
 */
data class RecognitionResult(
    val faceId: Int,
    val personId: String?,
    val similarity: Float,
    val isRecognized: Boolean
) {
    init {
        require(similarity.isFinite()) {
            "相似度不可為 NaN 或 Infinity"
        }

        require(similarity in -1.0f..1.0f) {
            "相似度必須介於 -1.0 與 1.0 之間"
        }

        require(!isRecognized || !personId.isNullOrBlank()) {
            "辨識成功時必須提供 personId"
        }
    }
}
