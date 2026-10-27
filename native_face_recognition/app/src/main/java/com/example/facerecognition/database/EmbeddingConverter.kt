
package com.example.facerecognition.database

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EmbeddingConverter
 *
 * 組員C：人臉特徵向量格式轉換工具
 *
 * 功能：
 * 1. FloatArray(128) → ByteArray(512)
 * 2. ByteArray(512) → FloatArray(128)
 *
 * 用途：
 * 將 MobileFaceNet 的特徵向量
 * 儲存到 SQLite 的 BLOB 欄位，
 * 並在讀取時還原成 FloatArray。
 */
object EmbeddingConverter {

    // MobileFaceNet 特徵向量維度
    private const val EMBEDDING_SIZE = 128

    // 每個 Float32 佔用 4 bytes
    private const val BYTES_PER_FLOAT = 4

    // 128 × 4 = 512 bytes
    private const val TOTAL_BYTES =
        EMBEDDING_SIZE * BYTES_PER_FLOAT

    /**
     * 將 128 維特徵向量轉為 ByteArray。
     *
     * @param embedding MobileFaceNet 輸出的 FloatArray
     * @return 可儲存於 SQLite BLOB 的 ByteArray
     */
    fun toByteArray(embedding: FloatArray): ByteArray {

        // 確認向量長度正確
        require(embedding.size == EMBEDDING_SIZE) {
            "特徵向量必須為 128 維"
        }

        // 確認每個數值都是有效浮點數
        require(embedding.all { it.isFinite() }) {
            "特徵向量包含 NaN 或 Infinity"
        }

        // 建立固定大小的 ByteBuffer
        // 明確使用 LITTLE_ENDIAN，避免跨裝置格式不一致
        val buffer = ByteBuffer.allocate(TOTAL_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

        // 依序把 128 個 Float32 寫入 ByteBuffer
        for (value in embedding) {
            buffer.putFloat(value)
        }

        // 轉換成 ByteArray，提供 SQLite 儲存
        return buffer.array()
    }

    /**
     * 將 SQLite BLOB 還原成 128 維特徵向量。
     *
     * @param bytes 從 SQLite 讀出的 ByteArray
     * @return FloatArray(128)
     */
    fun fromByteArray(bytes: ByteArray): FloatArray {

        // 確認 BLOB 資料大小為 512 bytes
        require(bytes.size == TOTAL_BYTES) {
            "特徵向量 BLOB 必須為 512 bytes"
        }

        // 使用與寫入時相同的 LITTLE_ENDIAN 順序
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        // 建立 128 維 FloatArray
        val embedding = FloatArray(EMBEDDING_SIZE)

        // 依序讀回每個 Float32
        for (index in 0 until EMBEDDING_SIZE) {
            embedding[index] = buffer.float
        }

        // 確認還原後的數值皆有效
        require(embedding.all { it.isFinite() }) {
            "還原後的特徵向量包含無效數值"
        }

        return embedding
    }
}
