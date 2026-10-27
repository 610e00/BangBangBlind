
package com.example.facerecognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.facerecognition.crop.FaceCropper
import com.example.facerecognition.database.FamiliarDatabaseHelper
import com.example.facerecognition.database.FamiliarPerson
import com.example.facerecognition.database.FamiliarPersonDao
import com.example.facerecognition.database.FamiliarRecognitionRepository
import com.example.facerecognition.model.DetectedFace
import com.example.facerecognition.recognition.FaceRecognitionManager
import com.example.facerecognition.speech.FamiliarSpeechCoordinator
import com.example.facerecognition.speech.FamiliarTtsManager
import com.example.facerecognition.speech.RecognitionSpeechHandler
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import java.util.concurrent.Executors

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.facerecognition.voice.VoiceCommandManager


import com.example.facerecognition.trigger.RecognitionTriggerManager
import com.example.facerecognition.trigger.RecognitionTriggerSource

import android.content.Intent

import com.example.facerecognition.speech.SafetySpeechPriorityManager

import android.util.Log
import java.util.concurrent.RejectedExecutionException

/**
 * 組員C：真人辨識、SQLite 與 TTS 整合測試。
 *
 * 測試資料：
 * A1 註冊為媽媽，A2 用來辨識。
 * B1 註冊為朋友，B2 用來辨識。
 *
 * 使用獨立的測試資料庫，不修改正式熟人資料。
 */
class FamiliarSpeechIntegrationTestActivity : AppCompatActivity() {

    companion object {
        private const val TEST_DATABASE =
            "familiar_speech_integration_test.db"
    }

    // 使用單一背景執行緒執行模型與 SQLite 操作
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var databaseHelper: FamiliarDatabaseHelper
    private lateinit var personDao: FamiliarPersonDao
    private lateinit var recognitionManager: FaceRecognitionManager
    private lateinit var repository: FamiliarRecognitionRepository
    private lateinit var detector: FaceDetector

    private lateinit var ttsManager: FamiliarTtsManager
    private lateinit var speechCoordinator: FamiliarSpeechCoordinator

    private lateinit var safetyPriorityManager: SafetySpeechPriorityManager

    private lateinit var speechHandler: RecognitionSpeechHandler


    // 組員C：中文語音指令管理器
    private lateinit var voiceCommandManager: VoiceCommandManager

    // 組員C：統一管理語音、避障及手動辨識觸發
    private lateinit var triggerManager: RecognitionTriggerManager


    // 記錄語音指令要辨識的測試照片
    private val voiceTestPhoto = "person_a_2.jpg"

    /**
     * Android 麥克風權限申請。
     *
     * 使用者同意授權後，開始接收語音指令。
     */
    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                voiceCommandManager.startListening()
            } else {
                statusText.text =
                    "未取得麥克風權限，無法使用語音指令"
            }
        }


    private lateinit var statusText: TextView
    private lateinit var registerButton: Button
    private lateinit var momButton: Button
    private lateinit var friendButton: Button

    private var isPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 建立共用的安全語音狀態管理器
        safetyPriorityManager = SafetySpeechPriorityManager()

        // TTS 與語音協調器共用相同的安全狀態
        ttsManager = FamiliarTtsManager(
            context = this,
            safetyPriorityManager = safetyPriorityManager
        )


        // 初始化統一觸發管理器
        triggerManager = RecognitionTriggerManager { source ->

            // 目前使用測試照片驗證觸發流程
            // 真正的攝影機畫面之後再接入
            when (source) {

                RecognitionTriggerSource.VOICE -> {
                    recognizePhoto("person_a_2.jpg", 20)
                }

                RecognitionTriggerSource.MANUAL -> {
                    recognizePhoto("person_a_2.jpg", 10)
                }

                RecognitionTriggerSource.OBSTACLE -> {
                    recognizePhoto("person_b_2.jpg", 30)
                }
            }
        }



        // 初始化中文語音指令管理器
        voiceCommandManager = VoiceCommandManager(
            context = this,

            // 收到「辨識前方的人」指令時執行
            onRecognizeCommand = {


                if (isPrepared) {

                    // 語音指令統一交給觸發管理器
                    val result = triggerManager.requestRecognition(
                        RecognitionTriggerSource.VOICE
                    )

                    // 若未接受請求，顯示原因
                    if (result != com.example.facerecognition.trigger.RecognitionTriggerResult.ACCEPTED) {
                        statusText.text = "語音觸發結果：$result"
                    }

                } else {
                    statusText.text = "請先點擊「準備測試資料」"
                }
            },

            // 顯示語音辨識狀態
            onStatusChanged = { message ->
                statusText.text = message
            }
        )



        // 整合 TTS、10 秒防重複播報及安全語音狀態
        speechCoordinator = FamiliarSpeechCoordinator(
            ttsManager = ttsManager,
            safetyPriorityManager = safetyPriorityManager
        )
        // 建立測試畫面
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "熟人辨識與語音整合測試"
            textSize = 22f
        }

        statusText = TextView(this).apply {
            text = "請先點擊「準備測試資料」"
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }

        registerButton = Button(this).apply {
            text = "1. 準備測試資料"
            setOnClickListener {
                prepareTestData()
            }
        }

        momButton = Button(this).apply {
            text = "2. 辨識 A2（預期媽媽）"
            isEnabled = false


            setOnClickListener {

                // 手動請求也由統一管理器處理
                val result = triggerManager.requestRecognition(
                    RecognitionTriggerSource.MANUAL
                )

                if (result != com.example.facerecognition.trigger.RecognitionTriggerResult.ACCEPTED) {
                    statusText.text = "手動觸發結果：$result"
                }
            }
        }

        friendButton = Button(this).apply {
            text = "3. 辨識 B2（預期朋友）"
            isEnabled = false

            setOnClickListener {
                recognizePhoto("person_b_2.jpg", 11)
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(registerButton)
        layout.addView(momButton)
        layout.addView(friendButton)

        // 建立語音指令測試按鈕
        val voiceButton = Button(this).apply {

            text = "4. 語音指令：辨識前方的人"

            setOnClickListener {

                // 確認已經準備好 SQLite 測試熟人
                if (!isPrepared) {
                    statusText.text =
                        "請先點擊「準備測試資料」"
                    return@setOnClickListener
                }

                // 檢查麥克風權限
                val hasPermission =
                    ContextCompat.checkSelfPermission(
                        this@FamiliarSpeechIntegrationTestActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {

                    // 已取得權限，開始語音辨識
                    voiceCommandManager.startListening()

                } else {

                    // 尚未授權，向使用者申請麥克風權限
                    microphonePermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            }
        }

        layout.addView(voiceButton)

        /**
         * 組員C：模擬避障模組觸發
         *
         * 目前使用 B2 測試照片，
         * 驗證 OBSTACLE 觸發來源能否啟動辨識。
         */
        val obstacleButton = Button(this).apply {

            text = "5. 模擬避障模組觸發辨識"

            setOnClickListener {

                // 統一交給 RecognitionTriggerManager
                val result = triggerManager.requestRecognition(
                    RecognitionTriggerSource.OBSTACLE
                )

                if (
                    result !=
                    com.example.facerecognition.trigger.RecognitionTriggerResult.ACCEPTED
                ) {
                    statusText.text = "避障觸發結果：$result"
                }
            }
        }

        /**
         * 模擬目前是否存在危險障礙物。
         *
         * 危險存在時，新的熟人辨識請求
         * 應被 BLOCKED_BY_DANGER 阻擋。
         */
        val dangerButton = Button(this).apply {

            text = "6. 模擬危險出現"

            var dangerPresent = false

            setOnClickListener {

                dangerPresent = !dangerPresent

                // 危險狀態同步通知辨識觸發管理器
                triggerManager.setDangerPresent(dangerPresent)

                // 危險狀態同步通知安全語音管理器
                ttsManager.updateDangerState(dangerPresent)

                if (dangerPresent) {
                    text = "6. 模擬危險解除"
                    statusText.text =
                        "危險狀態：有危險\n暫停新的熟人辨識"
                } else {
                    text = "6. 模擬危險出現"
                    statusText.text =
                        "危險狀態：已解除\n允許熟人辨識"
                }
            }
        }

        // 將兩個測試按鈕加入畫面
        layout.addView(obstacleButton)
        layout.addView(dangerButton)

        /**
         * 組員C：開啟熟人管理畫面
         *
         * 透過 Intent 切換至 FamiliarPeopleActivity，
         * 顯示正式 SQLite 資料庫中的熟人資料。
         */
        val familiarPeopleButton = Button(this).apply {

            text = "7. 開啟熟人管理"

            setOnClickListener {

                val intent = Intent(
                    this@FamiliarSpeechIntegrationTestActivity,
                    FamiliarPeopleActivity::class.java
                )

                startActivity(intent)
            }
        }

        layout.addView(familiarPeopleButton)




        setContentView(layout)
    }

    /**
     * 第一階段：
     * 初始化模型、建立測試 SQLite，
     * 並使用 A1、B1 註冊熟人。
     */
    private fun prepareTestData() {

        registerButton.isEnabled = false
        statusText.text = "正在註冊測試熟人..."

        executor.execute {
            try {
                // 只清除這支 Activity 專用的測試資料庫
                applicationContext.deleteDatabase(TEST_DATABASE)

                databaseHelper = FamiliarDatabaseHelper(
                    applicationContext,
                    TEST_DATABASE
                )

                personDao = FamiliarPersonDao(databaseHelper)

                recognitionManager = FaceRecognitionManager(
                    applicationContext,
                    0.80f
                )

                repository = FamiliarRecognitionRepository(
                    personDao,
                    recognitionManager
                )

                speechHandler = RecognitionSpeechHandler(
                    repository,
                    speechCoordinator
                )

                // 沿用組員A、B的 MediaPipe 設定
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(
                        "blaze_face_short_range.tflite"
                    )
                    .build()

                val detectorOptions =
                    FaceDetector.FaceDetectorOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.IMAGE)
                        .build()

                detector = FaceDetector.createFromOptions(
                    applicationContext,
                    detectorOptions
                )

                val faceA1 = loadNormalizedFace("person_a_1.jpg")
                val faceB1 = loadNormalizedFace("person_b_1.jpg")

                try {
                    // 透過組員B的模型產生真正的特徵向量
                    val embeddingA = recognitionManager.registerFace(
                        faceA1
                    )

                    val embeddingB = recognitionManager.registerFace(
                        faceB1
                    )

                    // 將特徵向量與測試稱呼儲存到 SQLite
                    check(
                        personDao.insertPerson(
                            FamiliarPerson(
                                personId = "person_a",
                                name = "測試人物A",
                                relationship = "媽媽",
                                embedding = embeddingA
                            )
                        )
                    ) {
                        "人物A寫入 SQLite 失敗"
                    }

                    check(
                        personDao.insertPerson(
                            FamiliarPerson(
                                personId = "person_b",
                                name = "測試人物B",
                                relationship = "朋友",
                                embedding = embeddingB
                            )
                        )
                    ) {
                        "人物B寫入 SQLite 失敗"
                    }

                    check(personDao.getAllPersons().size == 2) {
                        "SQLite 熟人數量不正確"
                    }

                    isPrepared = true

                    // SQLite 和模型準備完成後，
                    // 允許觸發管理器接受辨識請求
                    triggerManager.setReady(true)


                    runOnUiThread {
                        statusText.text =
                            "準備成功！SQLite 已註冊 2 位熟人。\n" +
                                    "現在可以開始辨識 A2 或 B2。"

                        momButton.isEnabled = true
                        friendButton.isEnabled = true
                    }

                } finally {
                    faceA1.recycle()
                    faceB1.recycle()
                }

            } catch (exception: Exception) {
                runOnUiThread {
                    statusText.text =
                        "準備失敗：${exception.message}"
                    registerButton.isEnabled = true
                }
            }
        }
    }

    /**
     * 第二階段：
     * 使用另一張真人照片執行辨識。
     *
     * 辨識結果不是由檔名決定，
     * 而是模型比較 SQLite 中的特徵向量。
     */
    private fun recognizePhoto(
        photoName: String,
        faceId: Int
    ) {
        if (!isPrepared) {
            statusText.text = "請先準備測試資料"
            return
        }

        momButton.isEnabled = false
        friendButton.isEnabled = false
        statusText.text = "正在辨識 $photoName ..."

        executor.execute {
            try {
                val queryFace = loadNormalizedFace(photoName)

                val result = try {
                    repository.recognize(
                        faceId = faceId,
                        faceBitmap = queryFace
                    )
                } finally {
                    queryFace.recycle()
                }

                // 根據辨識結果從 SQLite 查詢稱呼
                val displayName =
                    repository.getRecognizedPersonName(result)

                runOnUiThread {
                    // 在主執行緒觸發 TTS
                    val accepted =
                        speechHandler.handleRecognitionResult(result)

                    val identity =
                        displayName ?: "未知人物"

                    statusText.text =
                        "照片：$photoName\n" +
                                "辨識人物：$identity\n" +
                                "相似度：${result.similarity}\n" +
                                "辨識成功：${result.isRecognized}\n" +
                                "TTS 已接受播報：$accepted\n\n" +
                                "同一人物 10 秒內不重複播報。"

                    momButton.isEnabled = true
                    friendButton.isEnabled = true
                }

            } catch (exception: Exception) {
                runOnUiThread {
                    statusText.text =
                        "辨識失敗：${exception.message}"

                    momButton.isEnabled = true
                    friendButton.isEnabled = true
                }
            } finally {

                // 不論辨識成功或失敗，
                // 都解除觸發管理器的 BUSY 狀態
                if (::triggerManager.isInitialized) {
                    triggerManager.onRecognitionFinished()
                }
            }

        }
    }

    /**
     * 沿用 RealFaceTestActivity：
     * 讀圖 → 偵測 → 裁切 → 縮放 112×112。
     */
    private fun loadNormalizedFace(
        photoName: String
    ): Bitmap {

        val originalBitmap = assets.open(photoName).use {
            BitmapFactory.decodeStream(it)
        } ?: error("無法讀取照片：$photoName")

        try {
            val mpImage = BitmapImageBuilder(
                originalBitmap
            ).build()

            val detections = detector
                .detect(mpImage)
                .detections()

            require(detections.size == 1) {
                "$photoName 偵測到 ${detections.size} 張人臉"
            }

            val box = detections[0].boundingBox()

            val detectedFace = DetectedFace(
                faceId = 0,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )

            val faceCropper = FaceCropper()

            val croppedFace = faceCropper.cropFace(
                originalBitmap,
                detectedFace
            ) ?: error("$photoName 裁切失敗")

            val normalizedFace = try {
                faceCropper.resizeFace(croppedFace)
            } catch (exception: Exception) {
                croppedFace.recycle()
                throw exception
            }

            if (normalizedFace !== croppedFace) {
                croppedFace.recycle()
            }

            return normalizedFace

        } finally {
            originalBitmap.recycle()
        }
    }


    override fun onDestroy() {

        // 釋放語音辨識服務
        if (::voiceCommandManager.isInitialized) {
            voiceCommandManager.release()
        }

        // 停止並釋放 TTS
        if (::ttsManager.isInitialized) {
            ttsManager.release()
        }

        // 依序關閉模型及 SQLite 資料庫
        val cleanupTask = Runnable {

            try {
                if (::detector.isInitialized) {
                    detector.close()
                }
            } catch (e: Exception) {
                Log.e("IntegrationCleanup", "FaceDetector 關閉失敗", e)
            }

            try {
                if (::recognitionManager.isInitialized) {
                    recognitionManager.close()
                }
            } catch (e: Exception) {
                Log.e("IntegrationCleanup", "辨識模型關閉失敗", e)
            }

            try {
                if (::databaseHelper.isInitialized) {
                    databaseHelper.close()
                }
            } catch (e: Exception) {
                Log.e("IntegrationCleanup", "SQLite 關閉失敗", e)
            }
        }

        try {
            // 正常情況：排在已提交的工作後面清理
            executor.execute(cleanupTask)

        } catch (e: RejectedExecutionException) {
            // 避免提交失敗直接造成 Activity 崩潰。
            // 不在主執行緒強制關閉可能正在使用的資源。
            Log.e(
                "IntegrationCleanup",
                "清理任務提交失敗，需檢查 Executor 生命週期",
                e
            )
        } finally {
            executor.shutdown()
        }

        super.onDestroy()
    }
}
