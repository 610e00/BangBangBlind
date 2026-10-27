
package com.example.facerecognition

import com.example.facerecognition.database.EmbeddingConverter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 組員C：EmbeddingConverter 單元測試
 *
 * 確認：
 * 1. 128 維向量可以轉成 512 bytes
 * 2. ByteArray 可以還原為 FloatArray
 * 3. 轉換前後數值完全一致
 * 4. 錯誤資料會被正確拒絕
 */
class EmbeddingConverterTest {

    @Test
    fun testEmbeddingConversion() {

        // 建立 128 維測試向量
        val originalEmbedding = FloatArray(128) { index ->
            index.toFloat() / 100f
        }

        // 轉換成 SQLite BLOB 使用的 ByteArray
        val bytes = EmbeddingConverter.toByteArray(
            originalEmbedding
        )

        // 確認大小為 512 bytes
        assertEquals(512, bytes.size)

        // 將 ByteArray 還原成 FloatArray
        val restoredEmbedding = EmbeddingConverter.fromByteArray(
            bytes
        )

        // 確認還原後仍為 128 維
        assertEquals(128, restoredEmbedding.size)

        // 確認每個浮點數都完全一致
        assertArrayEquals(
            originalEmbedding,
            restoredEmbedding,
            0f
        )

        println("EmbeddingConverter 測試成功")
        println("原始向量維度：${originalEmbedding.size}")
        println("BLOB 大小：${bytes.size} bytes")
        println("還原向量維度：${restoredEmbedding.size}")
    }

    @Test
    fun testInvalidEmbeddingSize() {

        // 故意輸入錯誤的 127 維向量
        val invalidEmbedding = FloatArray(127)

        // 預期程式拒絕不符合格式的向量
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingConverter.toByteArray(invalidEmbedding)
        }
    }

    @Test
    fun testInvalidBlobSize() {

        // 故意使用錯誤長度的 BLOB
        val invalidBytes = ByteArray(100)

        // 預期程式拒絕錯誤的資料長度
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingConverter.fromByteArray(invalidBytes)
        }
    }
}
