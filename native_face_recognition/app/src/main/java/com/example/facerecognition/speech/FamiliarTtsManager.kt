
package com.example.facerecognition.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

import android.os.Bundle
import android.speech.tts.UtteranceProgressListener


/**
 * 組員C：熟人語音播報管理器
 *
 * 負責將熟人辨識結果轉換成中文語音。
 *
 * 注意：
 * TextToSpeech 需要非同步初始化，
 * 初始化成功後才能進行播報。
 */

class FamiliarTtsManager(
    context: Context,
    private val safetyPriorityManager: SafetySpeechPriorityManager =
        SafetySpeechPriorityManager()
) : TextToSpeech.OnInitListener {


    companion object {
        private const val TAG = "FamiliarTts"
    }

    // Android 內建 TTS 引擎
    private val textToSpeech = TextToSpeech(
        context.applicationContext,
        this
    )

    // 記錄 TTS 是否初始化成功
    @Volatile
    private var isReady = false
    /**
     * 查詢 TTS 是否已初始化完成。
     *
     * true：可以嘗試送出語音要求
     * false：尚未完成初始化或初始化失敗
     */
    fun isTtsReady(): Boolean {
        return isReady
    }

    // 安全警示是否正在處理中
    @Volatile
    private var isSafetyWarningActive = false

    // 保護語音請求與安全狀態的共用鎖
    private val speechLock = Any()

    // 每次安全警示使用不同 ID
    private var safetyWarningCounter = 0L

    // 目前最新安全警示的 ID
    private var currentSafetyWarningId: String? = null


    /**
     * TTS 初始化完成後呼叫。
     */
    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS 初始化失敗")
            return
        }

        // 設定為台灣繁體中文
        val result = textToSpeech.setLanguage(
            Locale.TAIWAN
        )

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.e(TAG, "裝置不支援繁體中文語音")
            return
        }

        // 設定語速
        textToSpeech.setSpeechRate(0.9f)

        // 監聽安全警示是否播報完成
        textToSpeech.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    // 目前不需要額外操作
                }

                override fun onDone(utteranceId: String?) {
                    finishSafetyWarning(utteranceId)
                }

                override fun onError(utteranceId: String?) {
                    finishSafetyWarning(utteranceId)
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {
                    finishSafetyWarning(utteranceId)
                }
            }
        )


        isReady = true

        Log.d(TAG, "TTS 初始化成功")
    }

    /**
     * 組員C：更新危險狀態（診斷版本）
     *
     * 只有安全狀態從 false 變成 true，
     * 才停止原本正在播放的語音。
     */
    @Synchronized
    fun updateDangerState(danger: Boolean) {

        val wasDangerPresent =
            safetyPriorityManager.isDangerPresent()

        Log.d(
            TAG,
            "危險狀態更新：原本=$wasDangerPresent，新狀態=$danger"
        )

        // 相同狀態不重複處理
        if (wasDangerPresent == danger) {

            Log.d(
                TAG,
                "危險狀態未改變，不執行 TTS.stop()"
            )

            return
        }

        safetyPriorityManager.setDangerPresent(danger)

        if (danger) {

            Log.w(
                TAG,
                "危險首次出現，執行 TTS.stop()"
            )

            textToSpeech.stop()
        }
    }

    @Synchronized
    fun speakPerson(displayName: String): Boolean {

        // 危險期間禁止熟人語音中斷安全警示
        if (!safetyPriorityManager.canAnnounceFamiliarPerson()) {
            Log.w(TAG, "目前有安全警示，阻擋熟人播報")
            return false
        }


        // TTS 尚未初始化完成，不執行播放
        if (!isReady) {
            Log.w(TAG, "TTS 尚未初始化完成")
            return false
        }

        // 避免播報空白稱呼
        if (displayName.isBlank()) {
            Log.w(TAG, "熟人稱呼不可為空白")
            return false
        }

        // 組合語音內容
        val message = "前方是$displayName"

        // 將語音播放要求送給 Android TTS

        val result = synchronized(speechLock) {

            // 安全警示正在播放時，
            // 不允許熟人語音中斷它
            if (isSafetyWarningActive) {

                Log.d(TAG, "安全警示優先，略過熟人播報")
                return false
            }

            textToSpeech.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "familiar_person"
            )
        }

        // 確認 TTS 是否接受要求
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS 播報要求失敗：$message")
            return false
        }

        Log.d(TAG, "TTS 已接受播報要求：$message")

        return true
    }

    /**
     * 停止目前的語音播報。
     */

    /**
     * 組員C：播放安全警示語音
     *
     * 使用與熟人播報相同的 TTS 引擎。
     *
     * QUEUE_FLUSH：
     * 安全警示可以中斷正在播放的熟人語音。
     *
     * 注意：
     * 此方法目前只負責送出安全警示，
     * 尚未完成熟人語音的優先權鎖定。
     */
    @Synchronized
    fun speakSafetyWarning(
        warningMessage: String
    ): Boolean {

        // 檢查 TTS 是否已初始化
        if (!isReady) {
            Log.w(TAG, "TTS 尚未初始化，無法播放安全警示")
            return false
        }

        // 避免空白警示內容
        if (warningMessage.isBlank()) {
            Log.w(TAG, "安全警示內容不可為空白")
            return false
        }

        // 使用同一個 TTS 引擎播報安全警示

        val result = synchronized(speechLock) {

            // 先鎖定一般熟人語音
            isSafetyWarningActive = true

            safetyWarningCounter++

            val warningId =
                "safety_warning_$safetyWarningCounter"

            currentSafetyWarningId = warningId


            // 安全警示出現時，先阻擋新的熟人播報
            safetyPriorityManager.setDangerPresent(true)

            // 安全警示可中斷既有熟人語音
            val speakResult = textToSpeech.speak(
                warningMessage,
                TextToSpeech.QUEUE_FLUSH,
                null,
                warningId
            )

            if (speakResult != TextToSpeech.SUCCESS) {
                isSafetyWarningActive = false
                currentSafetyWarningId = null
            }

            speakResult
        }

        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "安全警示播放要求失敗")
            return false
        }

        Log.d(TAG, "TTS 已接受安全警示：$warningMessage")

        return true
    }

    /**
     * 只在目前最新的安全警示結束時，
     * 才解除安全警示播放鎖定。
     */
    private fun finishSafetyWarning(utteranceId: String?) {

        synchronized(speechLock) {

            if (
                utteranceId != null &&
                utteranceId == currentSafetyWarningId
            ) {
                isSafetyWarningActive = false
                currentSafetyWarningId = null

                Log.d(TAG, "安全警示播報已結束")
            }
        }
    }

    fun stop() {
        textToSpeech.stop()
    }

    /**
     * 釋放 TTS 資源。
     *
     * Activity 結束時應呼叫。
     */
    fun release() {
        isReady = false
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
