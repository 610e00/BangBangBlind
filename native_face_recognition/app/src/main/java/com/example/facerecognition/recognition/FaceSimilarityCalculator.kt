
package com.example.facerecognition.recognition

import kotlin.math.sqrt

/**
 * FaceSimilarityCalculator
 *
 * 組員B：人臉特徵向量相似度計算工具
 *
 * 功能：
 * 1. 接收兩組人臉特徵向量
 * 2. 使用 Cosine Similarity 計算相似度
 * 3. 回傳 -1.0 ~ 1.0 之間的數值
 *
 * 注意：
 * 相似度越高代表特徵向量方向越相近，
 * 不代表該數值就是辨識正確率。
 */
class FaceSimilarityCalculator {

    /**
     * 計算兩組特徵向量的餘弦相似度。
     *
     * @param firstEmbedding 第一組特徵向量
     * @param secondEmbedding 第二組特徵向量
     * @return 餘弦相似度
     */
    fun calculateSimilarity(
        firstEmbedding: FloatArray,
        secondEmbedding: FloatArray
    ): Float {

        // 確認兩組向量長度相同且不為空
        require(
            firstEmbedding.isNotEmpty() &&
                    firstEmbedding.size == secondEmbedding.size
        ) {
            "兩組特徵向量必須具有相同且非零的維度"
        }

        var dotProduct = 0.0
        var firstMagnitudeSquared = 0.0
        var secondMagnitudeSquared = 0.0

        // 計算內積及各向量的長度平方
        for (index in firstEmbedding.indices) {
            val firstValue = firstEmbedding[index].toDouble()
            val secondValue = secondEmbedding[index].toDouble()

            require(firstValue.isFinite() && secondValue.isFinite()) {
                "特徵向量包含無效數值"
            }

            dotProduct += firstValue * secondValue
            firstMagnitudeSquared += firstValue * firstValue
            secondMagnitudeSquared += secondValue * secondValue
        }

        // 零向量無法計算餘弦相似度
        require(
            firstMagnitudeSquared > 0.0 &&
                    secondMagnitudeSquared > 0.0
        ) {
            "特徵向量長度不可為零"
        }

        // Cosine Similarity = A·B / (|A| × |B|)
        val similarity = dotProduct / (
                sqrt(firstMagnitudeSquared) *
                        sqrt(secondMagnitudeSquared)
                )

        // 避免浮點數誤差讓結果略微超出 -1 ~ 1
        return similarity.coerceIn(-1.0, 1.0).toFloat()
    }
}
