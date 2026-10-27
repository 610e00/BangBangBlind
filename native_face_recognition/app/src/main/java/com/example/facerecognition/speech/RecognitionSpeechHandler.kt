
package com.example.facerecognition.speech

import com.example.facerecognition.database.FamiliarRecognitionRepository
import com.example.facerecognition.recognition.RecognitionResult

/**
 * 組員C：辨識結果語音處理器
 *
 * 負責串接：
 * 1. 組員B的 RecognitionResult
 * 2. 組員C的 SQLite 熟人資料
 * 3. 防重複播報機制
 * 4. Android TTS 語音播報
 *
 * 此類別不負責執行人臉模型推論，
 * 而是接收已完成的辨識結果。
 */
class RecognitionSpeechHandler(
    private val repository: FamiliarRecognitionRepository,
    private val speechCoordinator: FamiliarSpeechCoordinator
) {

    /**
     * 處理一次人臉辨識結果。
     *
     * @return true：
     * 成功向 TTS 送出熟人播報要求。
     *
     * false：
     * 未知人物、資料庫找不到人物、
     * 冷卻中，或 TTS 尚未準備完成。
     */
    fun handleRecognitionResult(
        result: RecognitionResult
    ): Boolean {

        // 未辨識成功，不播報
        if (!result.isRecognized) {
            return false
        }

        // 取得辨識出的人物 ID
        val personId = result.personId ?: return false

        // 根據 personId 從 SQLite 查詢熟人稱呼
        val displayName =
            repository.getRecognizedPersonName(result)
                ?: return false

        // 交給防重複播報與 TTS 管理器
        return speechCoordinator.announcePerson(
            personId = personId,
            displayName = displayName
        )
    }
}
