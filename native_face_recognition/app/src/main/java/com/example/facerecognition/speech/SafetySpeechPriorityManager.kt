
package com.example.facerecognition.speech

/**
 * 組員C：安全語音優先權狀態管理器
 *
 * 目前只管理是否允許熟人播報。
 *
 * 尚未真正控制 TTS 的播放、中斷與排序。
 */
class SafetySpeechPriorityManager {

    // 避障模組是否回報危險
    private var dangerPresent = false

    /**
     * 更新危險狀態。
     */
    @Synchronized
    fun setDangerPresent(danger: Boolean) {
        dangerPresent = danger
    }

    /**
     * 危險存在時，不允許新的熟人播報。
     */
    @Synchronized
    fun canAnnounceFamiliarPerson(): Boolean {
        return !dangerPresent
    }

    /**
     * 提供其他模組查詢目前危險狀態。
     */
    @Synchronized
    fun isDangerPresent(): Boolean {
        return dangerPresent
    }
}