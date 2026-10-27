
package com.example.facerecognition.speech

/**
 * 組員C：熟人語音協調管理器
 *
 * 負責整合：
 * 1. 防重複播報
 * 2. Android TTS
 *
 * 目前只接收人物 ID 與稱呼。
 * 後續再串接真正的 RecognitionResult。
 */

class FamiliarSpeechCoordinator(
    private val ttsManager: FamiliarTtsManager,
    private val announcementController: FamiliarAnnouncementController =
        FamiliarAnnouncementController(),

    // 組員C：安全語音優先權管理器
    private val safetyPriorityManager: SafetySpeechPriorityManager =
        SafetySpeechPriorityManager()
) {

    /**
     * 嘗試播報指定熟人。
     *
     * @param personId 人物唯一識別碼
     * @param displayName 播報稱呼，例如「媽媽」
     *
     * @return true：成功送出 TTS 播報要求
     *         false：冷卻中或 TTS 未接受要求
     */
    @Synchronized
    fun announcePerson(
        personId: String,
        displayName: String
    ): Boolean {

        // 安全警示優先：危險存在時不播放熟人語音
        if (!safetyPriorityManager.canAnnounceFamiliarPerson()) {
            return false
        }

        // 避免空白人物 ID
        require(personId.isNotBlank()) {
            "personId 不可為空白"
        }

        // 沒有有效稱呼就不播報
        if (displayName.isBlank()) {
            return false
        }

        // 先檢查 10 秒冷卻機制
        if (!announcementController.shouldAnnounce(personId)) {
            return false
        }

        // 呼叫 Android TTS 進行播報
        val accepted = try {
            ttsManager.speakPerson(displayName)
        } catch (exception: Exception) {
            false
        }

        // TTS 未接受要求時，撤銷這次冷卻紀錄
        if (!accepted) {
            announcementController.resetPerson(personId)
        }

        return accepted
    }

    /**
     * 清除指定人物的冷卻紀錄。
     */
    fun resetPerson(personId: String) {
        announcementController.resetPerson(personId)
    }

    /**
     * 清除所有人物的冷卻紀錄。
     */
    fun resetAll() {
        announcementController.resetAll()
    }
}
