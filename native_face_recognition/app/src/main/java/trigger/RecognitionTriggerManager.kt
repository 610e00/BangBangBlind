
package com.example.facerecognition.trigger

/**
 * 組員C：熟人辨識觸發來源
 *
 * VOICE：語音指令
 * OBSTACLE：避障模組
 * MANUAL：手動按鈕
 */
enum class RecognitionTriggerSource {
    VOICE,
    OBSTACLE,
    MANUAL
}

/**
 * 組員C：觸發請求的處理結果
 */
enum class RecognitionTriggerResult {

    // 成功接受辨識請求
    ACCEPTED,

    // 前一次辨識尚未完成
    BUSY,

    // 目前有危險障礙物，不執行熟人辨識
    BLOCKED_BY_DANGER,

    // 觸發時沒有可用的辨識功能
    NOT_READY,

    // 呼叫辨識功能時發生錯誤
    FAILED
}

/**
 * 組員C：統一熟人辨識觸發管理器
 *
 * 功能：
 * 1. 統一語音、避障、手動觸發入口
 * 2. 避免同時重複啟動辨識
 * 3. 危險障礙物存在時暫停熟人辨識
 *
 * 本類別不直接操作攝影機、SQLite 或 TTS。
 *
 * onRecognitionRequested：
 * 外部 Activity 收到請求後，
 * 負責啟動真正的人臉辨識。
 *
 * 限制：
 * onRecognitionRequested 必須快速提交辨識工作，
 * 不應在回呼內直接執行耗時的模型推論。
 */
class RecognitionTriggerManager(
    private val onRecognitionRequested:
        (RecognitionTriggerSource) -> Unit
) {

    // 是否正在進行辨識
    private var isRecognizing = false

    // 避障模組是否回報目前存在危險
    private var isDangerPresent = false

    // 是否已完成模型、資料庫等初始化
    private var isReady = false

    /**
     * 設定辨識模組是否準備完成。
     */
    @Synchronized
    fun setReady(ready: Boolean) {
        isReady = ready
    }

    /**
     * 接收避障模組的危險狀態。
     *
     * true：目前有危險，暫停新的人臉辨識
     * false：危險解除，可以接受辨識請求
     *
     * 注意：
     * 這只會阻擋新的請求，
     * 不會取消已經執行中的模型運算。
     */
    @Synchronized
    fun setDangerPresent(danger: Boolean) {
        isDangerPresent = danger
    }

    /**
     * 統一處理所有辨識觸發來源。
     *
     * 接受請求時會將 isRecognizing 設為 true。
     *
     * 真正的辨識工作完成後，
     * 外部必須呼叫 onRecognitionFinished()。
     */
    fun requestRecognition(
        source: RecognitionTriggerSource
    ): RecognitionTriggerResult {

        synchronized(this) {

            // 系統尚未準備完成
            if (!isReady) {
                return RecognitionTriggerResult.NOT_READY
            }

            // 危險警示優先於熟人辨識
            if (isDangerPresent) {
                return RecognitionTriggerResult.BLOCKED_BY_DANGER
            }

            // 前一次辨識仍在執行
            if (isRecognizing) {
                return RecognitionTriggerResult.BUSY
            }

            // 接受請求，鎖定辨識狀態
            isRecognizing = true
        }

        return try {

            // 通知外部 Activity 啟動辨識
            onRecognitionRequested(source)

            RecognitionTriggerResult.ACCEPTED

        } catch (exception: Exception) {

            // 呼叫失敗時釋放辨識狀態
            onRecognitionFinished()

            RecognitionTriggerResult.FAILED
        }
    }

    /**
     * 辨識流程完成或失敗時呼叫。
     *
     * 必須在成功與失敗兩種情況下執行，
     * 建議放在辨識工作的 finally 區塊。
     */
    @Synchronized
    fun onRecognitionFinished() {
        isRecognizing = false
    }

    /**
     * 取得目前是否正在辨識。
     */
    @Synchronized
    fun isRecognitionRunning(): Boolean {
        return isRecognizing
    }
}
