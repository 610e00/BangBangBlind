
package com.example.facerecognition.speech

import android.os.SystemClock

/**
 * 組員C：熟人防重複播報管理器
 *
 * 目的：
 * 避免同一位熟人在短時間內反覆播報。
 *
 * 規則：
 * 1. 第一次辨識到人物 → 可以播報
 * 2. 同一人物在 10 秒內 → 不重複播報
 * 3. 超過 10 秒 → 可以再次播報
 * 4. 不同人物 → 分別計算冷卻時間
 *
 * 注意：
 * 本類別只負責決定是否允許播報，
 * 不直接執行 TTS。
 */
class FamiliarAnnouncementController(
    private val cooldownMillis: Long = 10_000L,
    private val currentTimeMillis: () -> Long = {
        SystemClock.elapsedRealtime()
    }
) {

    init {
        require(cooldownMillis >= 0) {
            "冷卻時間不能為負數"
        }
    }

    // 記錄每位人物上次允許播報的時間
    private val lastAnnouncementTimes =
        mutableMapOf<String, Long>()

    /**
     * 判斷指定人物是否可以播報。
     *
     * @param personId 熟人的唯一識別碼
     * @return true：可以播報
     *         false：仍在冷卻時間內
     */
    @Synchronized
    fun shouldAnnounce(personId: String): Boolean {

        require(personId.isNotBlank()) {
            "personId 不可為空白"
        }

        val now = currentTimeMillis()

        val lastTime = lastAnnouncementTimes[personId]

        // 第一次辨識到這位人物
        if (lastTime == null) {
            lastAnnouncementTimes[personId] = now
            return true
        }

        // 冷卻時間已經過
        if (
            now >= lastTime &&
            now - lastTime >= cooldownMillis
        ) {
            lastAnnouncementTimes[personId] = now
            return true
        }

        // 冷卻期間不允許重複播報
        return false
    }

    /**
     * 清除某位人物的播報紀錄。
     *
     * 例如重新註冊人物時，可以重設。
     */
    @Synchronized
    fun resetPerson(personId: String) {
        lastAnnouncementTimes.remove(personId)
    }

    /**
     * 清除所有人物的播報紀錄。
     */
    @Synchronized
    fun resetAll() {
        lastAnnouncementTimes.clear()
    }
}
