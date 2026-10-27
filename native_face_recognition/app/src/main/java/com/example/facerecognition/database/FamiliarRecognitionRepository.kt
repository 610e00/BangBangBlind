
package com.example.facerecognition.database

import android.graphics.Bitmap
import com.example.facerecognition.recognition.FaceRecognitionManager
import com.example.facerecognition.recognition.RecognitionResult

/**
 * 組員C：熟人辨識資料整合類別
 *
 * 負責：
 * 1. 從 SQLite 讀取已註冊熟人
 * 2. 將特徵向量提供給 FaceRecognitionManager
 * 3. 根據辨識結果查詢人物稱呼
 *
 * 注意：
 * 傳入的 Bitmap 必須是已裁切、縮放完成的
 * 112 × 112 人臉圖片。
 */
class FamiliarRecognitionRepository(
    private val personDao: FamiliarPersonDao,
    private val recognitionManager: FaceRecognitionManager
) {

    /**
     * 讀取全部已註冊熟人的特徵向量。
     *
     * 回傳格式：
     * Map<personId, FloatArray>
     */
    fun getRegisteredEmbeddings(): Map<String, FloatArray> {

        return personDao.getAllPersons().associate { person ->

            // 使用 copyOf 避免外部直接修改原始陣列
            person.personId to person.embedding.copyOf()
        }
    }

    /**
     * 執行熟人辨識。
     *
     * faceId：由偵測模組提供的人臉 ID
     * faceBitmap：112 × 112 人臉圖片
     */
    fun recognize(
        faceId: Int,
        faceBitmap: Bitmap
    ): RecognitionResult {

        // 從 SQLite 取得全部熟人向量
        val registeredEmbeddings = getRegisteredEmbeddings()

        // 呼叫組員B的統一辨識介面
        return recognitionManager.recognizeFace(
            faceId = faceId,
            faceBitmap = faceBitmap,
            registeredEmbeddings = registeredEmbeddings
        )
    }

    /**
     * 根據辨識結果取得熟人稱呼。
     *
     * 辨識失敗或查不到人物時，回傳 null。
     */
    fun getRecognizedPersonName(
        result: RecognitionResult
    ): String? {

        // 未通過辨識門檻，不提供人物稱呼
        if (!result.isRecognized) {
            return null
        }

        // 辨識成功才會有 personId
        val personId = result.personId ?: return null

        // 從 SQLite 查詢熟人
        val person = personDao.getPersonById(personId)
            ?: return null

        // 優先取得關係稱呼，例如「媽媽」
        return person.getDisplayName()
    }
}
