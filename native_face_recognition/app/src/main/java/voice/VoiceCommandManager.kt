
package com.example.facerecognition.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 組員C：中文語音指令管理器
 *
 * 功能：
 * 1. 啟動 Android 語音辨識
 * 2. 取得中文語音轉文字結果
 * 3. 判斷是否為「辨識前方的人」指令
 * 4. 通知 Activity 執行人臉辨識
 *
 * 注意：
 * 麥克風權限由 Activity 負責申請。
 * 本類別應在 Android 主執行緒使用。
 */
class VoiceCommandManager(
    private val context: Context,
    private val onRecognizeCommand: () -> Unit,
    private val onStatusChanged: (String) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null

    private var isListening = false

    /**
     * 開始聆聽中文語音指令。
     */
    fun startListening() {

        // 檢查麥克風權限
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            onStatusChanged("尚未取得麥克風權限")
            return
        }

        // 避免重複啟動
        if (isListening) {
            onStatusChanged("目前正在聆聽語音")
            return
        }

        // 檢查裝置是否有語音辨識服務
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatusChanged("裝置沒有可用的語音辨識服務")
            return
        }

        try {
            // 第一次使用時才建立 SpeechRecognizer
            if (speechRecognizer == null) {
                speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceCommandManager)
                    }
            }

            // 設定中文語音辨識
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.TAIWAN.toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )
            }

            isListening = true
            onStatusChanged("正在聆聽，請說：辨識前方的人")

            speechRecognizer?.startListening(intent)

        } catch (exception: Exception) {
            isListening = false

            onStatusChanged(
                "啟動語音辨識失敗：${exception.message}"
            )
        }
    }

    /**
     * 停止本次聆聽。
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    /**
     * 語音服務準備完成。
     */
    override fun onReadyForSpeech(params: Bundle?) {
        onStatusChanged("麥克風已準備好，請開始說話")
    }

    /**
     * 偵測到使用者開始說話。
     */
    override fun onBeginningOfSpeech() {
        onStatusChanged("正在接收語音...")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // 暫時不顯示音量變化
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // 暫時不處理原始音訊
    }

    /**
     * 使用者停止說話。
     */
    override fun onEndOfSpeech() {
        onStatusChanged("語音接收完成，正在辨識...")
    }

    /**
     * 語音辨識發生錯誤。
     */

    /**
     * 語音辨識發生錯誤時，
     * 顯示使用者能理解的提示。
     */
    override fun onError(error: Int) {

        isListening = false

        val message = when (error) {

            SpeechRecognizer.ERROR_NO_MATCH ->
                "沒有辨識到有效語音，請再說一次：辨識前方的人"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "沒有偵測到說話聲音，請重新點擊語音按鈕"

            SpeechRecognizer.ERROR_AUDIO ->
                "麥克風音訊處理失敗，請檢查麥克風"

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "語音辨識網路異常，請稍後再試"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "語音辨識服務忙碌中，請稍後再試"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "沒有麥克風權限，請檢查應用程式權限"

            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER ->
                "語音辨識服務發生錯誤，請重新嘗試"

            else ->
                "語音辨識失敗，錯誤代碼：$error"
        }

        onStatusChanged(message)
    }

    /**
     * 收到語音辨識結果。
     */
    override fun onResults(results: Bundle?) {

        isListening = false

        val candidates = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        ).orEmpty()

        if (candidates.isEmpty()) {
            onStatusChanged("沒有辨識到語音內容")
            return
        }

        // 使用第一個結果顯示辨識文字
        val recognizedText = candidates.first()

        // 檢查候選結果中是否有符合的指令
        val isCommandMatched = candidates.any { text ->
            isRecognizeCommand(text)
        }

        if (isCommandMatched) {
            onStatusChanged(
                "收到語音指令：$recognizedText\n準備執行熟人辨識"
            )

            // 將指令通知給 Activity
            onRecognizeCommand()

        } else {
            onStatusChanged(
                "辨識到：$recognizedText\n不是熟人辨識指令"
            )
        }
    }

    /**
     * 暫時不使用部分辨識結果。
     */
    override fun onPartialResults(partialResults: Bundle?) {
        // 等待完整結果
    }

    override fun onEvent(
        eventType: Int,
        params: Bundle?
    ) {
        // 暫時不處理其他事件
    }

    /**
     * 判斷文字是否為熟人辨識指令。
     *
     * 支援：
     * 「辨識前方的人」
     * 「辨識前面的人」
     * 「辨識熟人」
     * 「認識前方的人嗎」
     */
    private fun isRecognizeCommand(text: String): Boolean {

        // 移除常見空格與標點
        val normalized = text
            .replace(Regex("[\\s，。！？、,.!?]"), "")

        return normalized.contains("辨識前方的人") ||
                normalized.contains("辨識前面的人") ||
                normalized.contains("辨識熟人") ||
                normalized.contains("認識前方的人嗎")
    }

    /**
     * Activity 結束時釋放語音辨識資源。
     */
    fun release() {

        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
