
package com.example.facerecognition.recognition

/**
 * FaceMatcher
 *
 * 組員B：已註冊人物特徵比對模組
 *
 * 功能：
 * 1. 接收待辨識人臉的 128 維特徵向量
 * 2. 與已註冊人物的特徵向量逐一比較
 * 3. 找出相似度最高的人物
 * 4. 根據 Similarity Threshold 產生 RecognitionResult
 *
 * 注意：
 * 本類別只負責特徵比對，不負責人臉偵測、
 * MobileFaceNet 推論或永久資料庫儲存。
 */
class FaceMatcher(
    private val similarityCalculator: FaceSimilarityCalculator =
        FaceSimilarityCalculator(),

    private val recognitionThreshold: FaceRecognitionThreshold =
        FaceRecognitionThreshold(0.80f)
) {

    /**
     * 比對待辨識人臉與已註冊人物。
     *
     * @param faceId 組員A的人臉追蹤 ID
     * @param queryEmbedding 待辨識人臉的特徵向量
     * @param registeredEmbeddings 已註冊人物的特徵向量
     *
     * Map 格式：
     * personId → FloatArray(128)
     *
     * @return RecognitionResult
     */
    fun match(
        faceId: Int,
        queryEmbedding: FloatArray,
        registeredEmbeddings: Map<String, FloatArray>
    ): RecognitionResult {

        // 確認待辨識向量維度正確
        require(queryEmbedding.size == 128) {
            "待辨識特徵向量必須為 128 維"
        }

        require(queryEmbedding.all { it.isFinite() }) {
            "待辨識特徵向量包含無效數值"
        }

        // 如果目前沒有任何已註冊人物
        if (registeredEmbeddings.isEmpty()) {
            return RecognitionResult(
                faceId = faceId,
                personId = null,
                similarity = 0f,
                isRecognized = false
            )
        }

        var bestPersonId: String? = null
        var highestSimilarity = -1.0f

        // 逐一比對每個已註冊人物
        for ((personId, registeredEmbedding) in registeredEmbeddings) {

            require(personId.isNotBlank()) {
                "已註冊人物 ID 不可為空白"
            }

            require(registeredEmbedding.size == 128) {
                "$personId 的特徵向量不是 128 維"
            }

            // 計算 Cosine Similarity
            val similarity =
                similarityCalculator.calculateSimilarity(
                    queryEmbedding,
                    registeredEmbedding
                )

            // 保留目前最高相似度及人物 ID
            if (bestPersonId == null || similarity > highestSimilarity) {
                highestSimilarity = similarity
                bestPersonId = personId
            }
        }

        // 判斷最高相似度是否通過門檻
        val isRecognized =
            recognitionThreshold.isMatch(highestSimilarity)

        // 將結果封裝成組員C可接收的格式
        return RecognitionResult(
            faceId = faceId,
            personId = if (isRecognized) bestPersonId else null,
            similarity = highestSimilarity,
            isRecognized = isRecognized
        )
    }
}
