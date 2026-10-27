
package com.example.facerecognition

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.facerecognition.speech.FamiliarAnnouncementController
import com.example.facerecognition.speech.FamiliarSpeechCoordinator
import com.example.facerecognition.speech.FamiliarTtsManager

/**
 * 組員C：TTS 與防重複播報整合測試畫面
 *
 * 測試項目：
 * 1. 第一次辨識熟人時可以播報
 * 2. 同一人物 10 秒內不重複播報
 * 3. 超過 10 秒後可以再次播報
 * 4. 可以手動重設冷卻紀錄
 */
class FamiliarTtsTestActivity : AppCompatActivity() {

    private lateinit var ttsManager: FamiliarTtsManager

    private lateinit var speechCoordinator:
            FamiliarSpeechCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Android TTS
        ttsManager = FamiliarTtsManager(this)

        // 建立 10 秒防重複播報管理器
        val announcementController =
            FamiliarAnnouncementController(
                cooldownMillis = 10_000L
            )

        // 整合 TTS 與防重複播報
        speechCoordinator = FamiliarSpeechCoordinator(
            ttsManager = ttsManager,
            announcementController = announcementController
        )

        // 建立垂直排列的測試畫面
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }

        // 標題
        val title = TextView(this).apply {
            text = "熟人防重複播報測試"
            textSize = 22f
        }

        // 測試說明
        val description = TextView(this).apply {
            text = "同一個人物 10 秒內只播報一次"
            textSize = 16f
        }

        // 顯示執行狀態
        val statusText = TextView(this).apply {
            text = "請按下方按鈕開始測試"
            textSize = 16f
            setPadding(0, 25, 0, 25)
        }

        // 測試人物A：媽媽
        val momButton = Button(this).apply {
            text = "播放：前方是媽媽"

            setOnClickListener {

                // 固定 ID 代表同一位人物
                val accepted = speechCoordinator.announcePerson(
                    personId = "person_mom",
                    displayName = "媽媽"
                )

                // 顯示是否成功送出播報
                statusText.text = if (accepted) {
                    "媽媽：已送出播報要求"
                } else {
                    "媽媽：冷卻中或 TTS 尚未準備完成"
                }
            }
        }

        // 測試人物B：朋友
        val friendButton = Button(this).apply {
            text = "播放：前方是朋友"

            setOnClickListener {

                val accepted = speechCoordinator.announcePerson(
                    personId = "person_friend",
                    displayName = "朋友"
                )

                statusText.text = if (accepted) {
                    "朋友：已送出播報要求"
                } else {
                    "朋友：冷卻中或 TTS 尚未準備完成"
                }
            }
        }

        // 清除冷卻紀錄
        val resetButton = Button(this).apply {
            text = "重設冷卻時間"

            setOnClickListener {

                speechCoordinator.resetAll()

                statusText.text = "冷卻紀錄已重設"
            }
        }

        // 停止目前正在播放的語音
        val stopButton = Button(this).apply {
            text = "停止播報"

            setOnClickListener {

                ttsManager.stop()

                statusText.text = "已要求停止播報"
            }
        }

        // 將元件加入測試畫面
        layout.addView(title)
        layout.addView(description)
        layout.addView(statusText)
        layout.addView(momButton)
        layout.addView(friendButton)
        layout.addView(resetButton)
        layout.addView(stopButton)

        setContentView(layout)
    }

    override fun onDestroy() {

        // Activity 結束時釋放 TTS
        if (::ttsManager.isInitialized) {
            ttsManager.release()
        }

        super.onDestroy()
    }
}
