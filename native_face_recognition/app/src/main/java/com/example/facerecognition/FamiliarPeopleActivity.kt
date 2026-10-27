
package com.example.facerecognition

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import android.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import com.example.facerecognition.database.FamiliarPerson

import com.example.facerecognition.database.FamiliarDatabaseHelper
import com.example.facerecognition.database.FamiliarPersonDao

import android.net.Uri
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts


import androidx.activity.ComponentActivity

import android.graphics.BitmapFactory

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

import com.example.facerecognition.crop.FaceCropper
import com.example.facerecognition.recognition.FaceRecognitionManager

import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector

import java.util.concurrent.Executors

import java.util.UUID

import com.example.facerecognition.database.FamiliarRecognitionRepository


import com.example.facerecognition.speech.FamiliarTtsManager
import com.example.facerecognition.speech.FamiliarSpeechCoordinator

import java.util.concurrent.RejectedExecutionException

import com.example.facerecognition.speech.SafetySpeechPriorityManager

class FamiliarPeopleActivity : ComponentActivity() {

    private lateinit var databaseHelper: FamiliarDatabaseHelper
    private lateinit var personDao: FamiliarPersonDao

    private lateinit var listContainer: LinearLayout
    private lateinit var countText: TextView

    // 組員C：中文語音播報
    private lateinit var ttsManager: FamiliarTtsManager

    // 組員C：管理 10 秒防重複播報
    private lateinit var speechCoordinator: FamiliarSpeechCoordinator

    // 組員C：熟人 TTS 共用的安全狀態管理器
    private lateinit var safetyPriorityManager: SafetySpeechPriorityManager


    // 組員C：避免使用者同時重複註冊熟人
    @Volatile
    private var isRegistering = false


    // 避免模型推論阻塞 Android 主畫面
    private val registrationExecutor =
        Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())


    // 暫存準備註冊的熟人資料
    private var pendingName = ""
    private var pendingRelationship = ""

    // 暫存使用者選取的照片 URI
    private var selectedPhotoUri: Uri? = null

    /**
     * Android 系統照片選擇器
     *
     * 不需要額外要求讀取整個相簿的權限。
     */
    private val photoPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                selectedPhotoUri = uri
                showSelectedPhotoPreview(uri)
            } else {
                Toast.makeText(
                    this,
                    "尚未選擇照片",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    /**
     * 組員C：辨識已註冊熟人的照片選擇器
     */
    private val recognitionPhotoPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {

                // 選擇照片後，開始正式熟人辨識
                recognizeRegisteredPerson(uri)

            } else {

                Toast.makeText(
                    this,
                    "已取消辨識照片選取",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    /**
     * 組員C：人臉相似度測試專用照片選擇器
     *
     * 只選擇測試照片，不新增 SQLite 資料。
     */
    private val similarityPhotoPickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->


            if (uri != null) {

                // 選取照片後開始計算相似度
                testPhotoSimilarity(uri)

            } else {

                Toast.makeText(
                    this,
                    "已取消相似度測試",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Android 中文 TTS

        // 先建立唯一的安全狀態管理器
        safetyPriorityManager = SafetySpeechPriorityManager()

        // TTS 與 Coordinator 共用相同安全狀態
        ttsManager = FamiliarTtsManager(
            context = this,
            safetyPriorityManager = safetyPriorityManager
        )

        speechCoordinator = FamiliarSpeechCoordinator(
            ttsManager = ttsManager,
            safetyPriorityManager = safetyPriorityManager
        )


        // 使用正式的熟人 SQLite 資料庫
        databaseHelper = FamiliarDatabaseHelper(this)
        personDao = FamiliarPersonDao(databaseHelper)

        // 建立垂直排列的主要畫面
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        // 畫面標題
        val titleText = TextView(this).apply {
            text = "熟人管理"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
        }

        mainLayout.addView(titleText)

        // 已註冊人數
        countText = TextView(this).apply {
            text = "讀取熟人資料中..."
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }

        mainLayout.addView(countText)

        // 重新整理按鈕
        val refreshButton = Button(this).apply {
            text = "重新整理熟人列表"

            setOnClickListener {
                loadFamiliarPeople()
            }
        }

        mainLayout.addView(refreshButton)


        // 組員C：新增熟人按鈕
        val addPersonButton = Button(this).apply {

            text = "新增熟人"


            setOnClickListener {

                // 已有註冊工作執行中，阻止重新開始
                if (isRegistering) {

                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "目前正在註冊熟人，請等待本次操作完成",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                // 沒有正在執行的註冊工作
                showAddPersonDialog()
            }
        }

        mainLayout.addView(addPersonButton)

        /**
         * 組員C：正式熟人辨識測試入口
         *
         * 下一步會接入照片選擇、模型辨識、
         * 正式 SQLite 查詢及 TTS。
         */
        val recognizePersonButton = Button(this).apply {

            text = "測試辨識已註冊熟人"


            setOnClickListener {

                // 開啟系統檔案選擇器
                // 選取照片後進入正式 SQLite 辨識流程
                recognitionPhotoPickerLauncher.launch(
                    arrayOf("image/*")
                )
            }

        }

        mainLayout.addView(recognizePersonButton)

        /**
         * 組員C：人臉特徵向量相似度測試
         */
        val similarityTestButton = Button(this).apply {

            text = "測試人臉相似度（不新增資料）"

            setOnClickListener {

                similarityPhotoPickerLauncher.launch(
                    arrayOf("image/*")
                )
            }
        }

        mainLayout.addView(similarityTestButton)

// 組員C：模擬避障危險狀態
        val safetyTestButton = Button(this).apply {

            text = "模擬危險出現"

            setOnClickListener {

                val newDangerState =
                    !safetyPriorityManager.isDangerPresent()


                ttsManager.updateDangerState(newDangerState)

                if (newDangerState) {

                    text = "模擬危險解除"

                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "目前有危險，已阻擋新的熟人語音",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {

                    text = "模擬危險出現"

                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "危險已解除，恢復允許熟人語音",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        mainLayout.addView(safetyTestButton)

        /**
         * 組員C：模擬播放避障安全警示
         */
        val warningTestButton = Button(this).apply {

            text = "播放安全警示（測試）"

            setOnClickListener {

                // 先標記危險，避免新的熟人語音插入

                ttsManager.updateDangerState(true)

                // 使用同一個 TTS 實例播放安全警示

                val success = ttsManager.speakSafetyWarning(
                    "注意！前方有障礙物"
                )
                if (!success) {
                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "安全警示未成功送出，請確認 TTS 已初始化",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        mainLayout.addView(warningTestButton)

        /**
         * 組員C：重複危險事件測試
         *
         * 只更新危險狀態，不重新播放警示。
         */
        val repeatedDangerTestButton = Button(this).apply {

            text = "重複回報危險（測試）"

            setOnClickListener {

                // 模擬避障模組再次回報相同危險
                ttsManager.updateDangerState(true)

                Toast.makeText(
                    this@FamiliarPeopleActivity,
                    "已再次回報危險狀態",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        mainLayout.addView(repeatedDangerTestButton)


        /**
         * 組員C：長語音中斷測試
         *
         * 只用來測試 TTS 優先權，
         * 不會修改 SQLite 或人臉辨識結果。
         */
        val longSpeechTestButton = Button(this).apply {

            text = "播放長篇熟人語音（測試）"

            setOnClickListener {

                // TTS 尚未初始化完成時，先提示使用者
                if (!ttsManager.isTtsReady()) {

                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "語音系統初始化中，請稍後再試",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                // 危險期間不允許播放一般語音
                if (!safetyPriorityManager.canAnnounceFamiliarPerson()) {

                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "目前有危險，無法播放熟人測試語音",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                // 測試用的較長文字，不會存入 SQLite
                val testMessage =
                    "母親，這是一段用來測試熟人語音播放優先順序的內容。" +
                            "當系統正在介紹前方熟人時，" +
                            "如果突然偵測到障礙物，" +
                            "系統應該立即停止一般語音，" +
                            "並優先提醒使用者注意前方的危險。" +
                            "這段內容只用於測試，不會在正式辨識時播放。"

                // 使用原本同一個 TTS 引擎
                val success = ttsManager.speakPerson(testMessage)

                if (!success) {
                    Toast.makeText(
                        this@FamiliarPeopleActivity,
                        "長語音未成功送出",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        mainLayout.addView(longSpeechTestButton)



        // 熟人資料列表
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(listContainer)
        }

        mainLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(mainLayout)

        // 首次進入畫面時讀取熟人
        loadFamiliarPeople()
    }

    /**
     * 讀取 SQLite 中所有已註冊熟人
     */
    private fun loadFamiliarPeople() {

        // 清除前一次顯示的資料
        listContainer.removeAllViews()

        try {
            val people = personDao.getAllPersons()

            countText.text = "已註冊熟人：${people.size} 人"

            if (people.isEmpty()) {

                val emptyText = TextView(this).apply {
                    text = "目前尚未註冊任何熟人"
                    textSize = 18f
                    setPadding(0, 48, 0, 0)
                }

                listContainer.addView(emptyText)
                return
            }

            for (person in people) {

                // 每位熟人的資料區塊
                val personLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 24, 20, 24)
                }

                val nameText = TextView(this).apply {
                    text = "姓名：${person.name}"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                }

                val relationshipText = TextView(this).apply {
                    text = "稱呼／關係：${person.relationship}"
                    textSize = 16f
                    setPadding(0, 8, 0, 8)
                }

                val idText = TextView(this).apply {
                    text = "人物 ID：${person.personId}"
                    textSize = 13f
                }

                personLayout.addView(nameText)
                personLayout.addView(relationshipText)
                personLayout.addView(idText)

                // 修改熟人資料按鈕
                val editButton = Button(this).apply {
                    text = "修改資料"

                    setOnClickListener {
                        showEditPersonDialog(person)
                    }
                }

                personLayout.addView(editButton)

                // 組員C：刪除熟人按鈕
                val deleteButton = Button(this).apply {

                    text = "刪除熟人"

                    setOnClickListener {

                        // 先顯示確認視窗，不直接刪除
                        showDeletePersonDialog(person)
                    }
                }

                personLayout.addView(deleteButton)



                listContainer.addView(personLayout)

                // 分隔線
                val divider = android.view.View(this).apply {
                    setBackgroundColor(0xFFCCCCCC.toInt())
                }

                listContainer.addView(
                    divider,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1
                    )
                )
            }

        } catch (exception: Exception) {

            countText.text = "熟人資料讀取失敗"

            val errorText = TextView(this).apply {
                text = exception.message ?: "未知錯誤"
                textSize = 14f
            }

            listContainer.addView(errorText)
        }
    }


    /**
     * 組員C：修改熟人姓名與稱呼
     *
     * 不修改 personId 或 embedding，
     * 避免影響已註冊的人臉特徵向量。
     */
    private fun showEditPersonDialog(person: FamiliarPerson) {

        // 建立輸入欄位的容器
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        // 姓名輸入框
        val nameInput = EditText(this).apply {
            hint = "請輸入姓名"
            setText(person.name)
            setSingleLine(true)
        }

        // 稱呼／關係輸入框
        val relationshipInput = EditText(this).apply {
            hint = "例如：媽媽、爸爸、朋友"
            setText(person.relationship)
            setSingleLine(true)
        }

        dialogLayout.addView(nameInput)
        dialogLayout.addView(relationshipInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("修改熟人資料")
            .setView(dialogLayout)
            .setNegativeButton("取消", null)
            .setPositiveButton("儲存", null)
            .create()

        // 顯示 Dialog 後再設定儲存按鈕，
        // 讓輸入錯誤時視窗不會直接關閉。
        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val newName = nameInput.text.toString().trim()
                val newRelationship =
                    relationshipInput.text.toString().trim()

                if (newName.isBlank()) {
                    nameInput.error = "姓名不能為空白"
                    return@setOnClickListener
                }

                try {
                    val success = personDao.updatePersonInfo(
                        personId = person.personId,
                        newName = newName,
                        newRelationship = newRelationship
                    )

                    if (success) {

                        Toast.makeText(
                            this,
                            "熟人資料修改成功",
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()

                        // 重新讀取 SQLite，顯示更新後資料
                        loadFamiliarPeople()

                    } else {

                        Toast.makeText(
                            this,
                            "修改失敗，找不到此熟人",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (exception: Exception) {

                    Toast.makeText(
                        this,
                        "修改失敗：${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }

    /**
     * 組員C：刪除熟人確認視窗
     *
     * 刪除前必須經過使用者確認。
     *
     * 刪除時會移除：
     * 1. personId
     * 2. 姓名
     * 3. 稱呼／關係
     * 4. 128維人臉特徵向量
     *
     * 不會刪除組員A、B的模型檔案。
     */
    private fun showDeletePersonDialog(person: FamiliarPerson) {

        AlertDialog.Builder(this)
            .setTitle("確認刪除熟人")
            .setMessage(
                "確定要刪除「${person.name}」嗎？\n\n" +
                        "稱呼：${person.getDisplayName()}\n\n" +
                        "刪除後，這位熟人的人臉特徵資料" +
                        "也會一併移除，之後需要重新註冊。"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("確認刪除") { _, _ ->

                try {

                    // 使用既有 DAO 刪除指定熟人
                    val success = personDao.deletePerson(
                        person.personId
                    )

                    if (success) {

                        Toast.makeText(
                            this,
                            "已刪除熟人：${person.name}",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 重新讀取 SQLite 熟人列表
                        loadFamiliarPeople()

                    } else {

                        Toast.makeText(
                            this,
                            "刪除失敗：找不到此熟人",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (exception: Exception) {

                    Toast.makeText(
                        this,
                        "刪除失敗：${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }


    /**
     * 組員C：新增熟人輸入視窗
     *
     * 目前只建立姓名與稱呼輸入欄位。
     * 下一步才會接入照片選擇與人臉註冊。
     */
    private fun showAddPersonDialog() {

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        // 熟人姓名
        val nameInput = EditText(this).apply {
            hint = "請輸入熟人姓名"
            setSingleLine(true)
        }

        // 稱呼或關係
        val relationshipInput = EditText(this).apply {
            hint = "例如：媽媽、爸爸、朋友"
            setSingleLine(true)
        }

        dialogLayout.addView(nameInput)
        dialogLayout.addView(relationshipInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("新增熟人")
            .setView(dialogLayout)
            .setNegativeButton("取消", null)
            .setPositiveButton("下一步", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val name = nameInput.text.toString().trim()
                val relationship =
                    relationshipInput.text.toString().trim()

                if (name.isBlank()) {
                    nameInput.error = "請輸入姓名"
                    return@setOnClickListener
                }

                // 目前先測試輸入資料是否正確。
                // 不會將沒有 embedding 的熟人寫入 SQLite。

                // 保存使用者輸入的姓名與稱呼
                pendingName = name
                pendingRelationship = relationship

                // 清除上次選取的照片
                selectedPhotoUri = null

                // 關閉輸入視窗
                dialog.dismiss()

                // 開啟系統照片選擇器
                photoPickerLauncher.launch("image/*")

            }
        }

        dialog.show()
    }


    /**
     * 組員C：顯示已選擇的人臉照片
     *
     * 本步驟只確認照片可以正常讀取，
     * 不執行人臉偵測或 SQLite 寫入。
     */
    private fun showSelectedPhotoPreview(uri: Uri) {

        val previewLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        // 顯示姓名與稱呼
        val infoText = TextView(this).apply {
            text = "姓名：$pendingName\n稱呼：$pendingRelationship"
            textSize = 17f
        }

        previewLayout.addView(infoText)

        // 顯示選取的人臉照片
        val imageView = ImageView(this).apply {

            adjustViewBounds = true

            scaleType = ImageView.ScaleType.FIT_CENTER

            setImageURI(uri)
        }

        previewLayout.addView(
            imageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600
            )
        )

        AlertDialog.Builder(this)
            .setTitle("確認人臉照片")
            .setView(previewLayout)
            .setNegativeButton("取消", null)


            .setPositiveButton("確認照片") { _, _ ->

                // 送入 MediaPipe + MobileFaceNet
                testGenerateFaceEmbedding(uri)
            }


            .show()
    }


    /**
     * 組員C：讀取使用者選取的照片
     *
     * 將 Uri 轉換成 Bitmap，
     * 供後續 MediaPipe 與 MobileFaceNet 使用。
     */
    private fun testLoadSelectedPhoto(uri: Uri) {

        try {

            val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }

            if (bitmap == null) {
                Toast.makeText(
                    this,
                    "照片讀取失敗",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val width = bitmap.width
            val height = bitmap.height

            Toast.makeText(
                this,
                "照片讀取成功！尺寸：${width} × ${height}",
                Toast.LENGTH_LONG
            ).show()

            // 本步驟只測試讀取，不進行模型推論
            bitmap.recycle()

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "照片讀取錯誤：${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * 組員C：測試照片註冊前的人臉特徵產生流程。
     *
     * 使用組員A的 MediaPipe、FaceCropper，
     * 以及組員B的 FaceRecognitionManager。
     *
     * 本步驟不寫入 SQLite。
     */
        private fun testGenerateFaceEmbedding(uri: Uri) {

            synchronized(this) {
                if (isRegistering) {
                    Toast.makeText(
                        this,
                        "正在註冊熟人，請勿重複操作",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                isRegistering = true
            }


            Toast.makeText(
                this,
                "正在偵測人臉並產生特徵向量...",
                Toast.LENGTH_SHORT
            ).show()

        try {
            registrationExecutor.execute {

                var detector: FaceDetector? = null
                var recognitionManager: FaceRecognitionManager? = null

                var originalBitmap: Bitmap? = null
                var croppedBitmap: Bitmap? = null
                var normalizedBitmap: Bitmap? = null

                try {
                    // 1. 從 Uri 讀取照片
                    originalBitmap =
                        contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        } ?: error("無法讀取照片")

                    // 2. 初始化組員A的 MediaPipe 模型
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath("blaze_face_short_range.tflite")
                        .build()

                    val detectorOptions =
                        FaceDetector.FaceDetectorOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.IMAGE)
                            .build()

                    detector = FaceDetector.createFromOptions(
                        this@FamiliarPeopleActivity,
                        detectorOptions
                    )

                    // 3. 偵測照片中的人臉
                    val mpImage =
                        BitmapImageBuilder(originalBitmap).build()

                    val detections = detector.detect(mpImage).detections()

                    if (detections.size != 1) {
                        error(
                            "需要剛好一張人臉，目前偵測到 ${detections.size} 張"
                        )
                    }

                    // 4. 取得第一張人臉的座標
                    val boundingBox = detections[0].boundingBox()

                    val detectedFace =
                        com.example.facerecognition.model.DetectedFace(
                            faceId = 1,
                            left = boundingBox.left.toFloat(),
                            top = boundingBox.top.toFloat(),
                            right = boundingBox.right.toFloat(),
                            bottom = boundingBox.bottom.toFloat()
                        )

                    // 5. 使用組員A的 FaceCropper
                    val cropper = FaceCropper()

                    croppedBitmap = cropper.cropFace(
                        originalBitmap,
                        detectedFace
                    ) ?: error("人臉裁切失敗")

                    normalizedBitmap = cropper.resizeFace(croppedBitmap)

                    // 6. 使用組員B的 MobileFaceNet
                    recognitionManager = FaceRecognitionManager(
                        this@FamiliarPeopleActivity
                    )

                    val embedding =
                        recognitionManager.registerFace(normalizedBitmap)

                    // 7. 確認產生128維有效向量
                    check(embedding.size == 128) {
                        "特徵向量維度異常：${embedding.size}"
                    }

                    check(embedding.all { it.isFinite() }) {
                        "特徵向量包含無效數值"
                    }

                    // 取得本次要註冊的姓名與稱呼
                    val name = pendingName.trim()
                    val relationship = pendingRelationship.trim()

                    check(name.isNotBlank()) {
                        "熟人姓名不能為空白"
                    }

                    // 為每位熟人產生唯一 ID
                    val personId = UUID.randomUUID().toString()

                    // 建立包含 128 維 embedding 的熟人資料
                    val newPerson = FamiliarPerson(
                        personId = personId,
                        name = name,
                        relationship = relationship,
                        embedding = embedding
                    )

                    // 使用正式 SQLite 資料庫
                    // 此處建立獨立的 Helper，
                    // 避免背景執行緒直接使用 Activity 持有的 DAO
                    val saveDatabaseHelper = FamiliarDatabaseHelper(
                        applicationContext
                    )


                    val inserted = try {

                        val savePersonDao =
                            FamiliarPersonDao(saveDatabaseHelper)

                        // 先取得 SQLite 所有已註冊熟人
                        val existingPeople =
                            savePersonDao.getAllPersons()

                        // 比較新照片與所有已註冊人臉
                        val duplicatePerson = existingPeople.firstOrNull {
                                existingPerson ->

                            val similarity = calculateEmbeddingSimilarity(
                                embedding,
                                existingPerson.embedding
                            )

                            // 暫時只阻擋幾乎完全相同的向量
                            similarity >= 0.9999
                        }

                        // 如果已經存在相同向量，停止註冊
                        if (duplicatePerson != null) {
                            error(
                                "疑似重複註冊！\n" +
                                        "已存在熟人：" +
                                        duplicatePerson.getDisplayName()
                            )
                        }

                        // 確認沒有重複後，才寫入 SQLite
                        savePersonDao.insertPerson(newPerson)

                    } finally {

                        // 無論成功或失敗都關閉資料庫
                        saveDatabaseHelper.close()
                    }


                    check(inserted) {
                        "SQLite 註冊失敗，資料未新增"
                    }


                    val imageSize =
                        "${normalizedBitmap.width} × ${normalizedBitmap.height}"


                    mainHandler.post {

                        if (isFinishing || isDestroyed) {
                            return@post
                        }

                        Toast.makeText(
                            this@FamiliarPeopleActivity,
                            "熟人註冊成功！\n" +
                                    "姓名：$name\n" +
                                    "稱呼：$relationship\n" +
                                    "向量維度：${embedding.size}",
                            Toast.LENGTH_LONG
                        ).show()

                        // 更新畫面，讓新熟人顯示在列表
                        loadFamiliarPeople()
                    }



                } catch (exception: Exception) {

                    // 註冊失敗時，在主畫面顯示錯誤
                    mainHandler.post {

                        if (!isFinishing && !isDestroyed) {

                            Toast.makeText(
                                this@FamiliarPeopleActivity,
                                "熟人註冊未完成：${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                } finally {

                    try {
                    // 即使其中一項清理失敗，
                    // 仍繼續嘗試釋放其他資源

                    try {
                        recognitionManager?.close()
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "FamiliarPeople",
                            "關閉辨識模型失敗",
                            e
                        )
                    }

                    try {
                        detector?.close()
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "FamiliarPeople",
                            "關閉人臉偵測器失敗",
                            e
                        )
                    }

                    try {
                        normalizedBitmap?.recycle()
                        croppedBitmap?.recycle()
                        originalBitmap?.recycle()
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "FamiliarPeople",
                            "釋放圖片資源失敗",
                            e
                        )
                    }

                    } finally {
                        // 無論資源清理是否成功，都解除註冊狀態
                        isRegistering = false
                    }

                } // 結束背景工作的 finally

            } // 結束 registrationExecutor.execute { ... }

        } catch (exception: RejectedExecutionException) {
            // 背景任務沒有成功提交，
            // 所以原本背景工作的 finally 不會執行
            isRegistering = false

            if (!isFinishing && !isDestroyed) {
                Toast.makeText(
                    this,
                    "無法啟動熟人註冊，請重新開啟畫面後再試",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }




    /**
     * 組員C：辨識正式 SQLite 中已註冊的熟人。
     *
     * 流程：
     * 照片 → MediaPipe → FaceCropper →
     * MobileFaceNet → SQLite 比對 → 顯示稱呼
     *
     * 本步驟不執行 TTS。
     */
    private fun recognizeRegisteredPerson(uri: Uri) {

        Toast.makeText(
            this,
            "正在辨識已註冊熟人...",
            Toast.LENGTH_SHORT
        ).show()

        registrationExecutor.execute {

            var detector: FaceDetector? = null
            var recognitionManager: FaceRecognitionManager? = null
            var recognitionDbHelper: FamiliarDatabaseHelper? = null

            var originalBitmap: Bitmap? = null
            var croppedBitmap: Bitmap? = null
            var normalizedBitmap: Bitmap? = null

            try {

                // 1. 讀取使用者選擇的照片
                originalBitmap =
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: error("無法讀取辨識照片")

                // 2. 初始化 MediaPipe
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

                // 3. 偵測人臉
                val mpImage =
                    BitmapImageBuilder(originalBitmap).build()

                val detections =
                    detector.detect(mpImage).detections()

                if (detections.size != 1) {
                    error(
                        "照片必須剛好有一張人臉，" +
                                "目前偵測到 ${detections.size} 張"
                    )
                }

                // 4. 建立組員A使用的人臉座標格式
                val box = detections[0].boundingBox()

                val detectedFace =
                    com.example.facerecognition.model.DetectedFace(
                        faceId = 1,
                        left = box.left.toFloat(),
                        top = box.top.toFloat(),
                        right = box.right.toFloat(),
                        bottom = box.bottom.toFloat()
                    )

                // 5. 裁切並縮放到 112 × 112
                val cropper = FaceCropper()

                croppedBitmap = cropper.cropFace(
                    originalBitmap,
                    detectedFace
                ) ?: error("裁切人臉失敗")

                normalizedBitmap =
                    cropper.resizeFace(croppedBitmap)

                // 6. 初始化組員B的人臉辨識模型
                recognitionManager = FaceRecognitionManager(
                    applicationContext
                )

                // 7. 使用正式資料庫
                recognitionDbHelper = FamiliarDatabaseHelper(
                    applicationContext
                )

                val recognitionDao =
                    FamiliarPersonDao(recognitionDbHelper)

                // 8. 使用之前已建立的 Repository
                val repository = FamiliarRecognitionRepository(
                    recognitionDao,
                    recognitionManager
                )

                // 9. 從 SQLite 讀取熟人並比對
                val result = repository.recognize(
                    faceId = 1,
                    faceBitmap = normalizedBitmap
                )

                // 10. 查詢辨識成功者的顯示稱呼
                val displayName =
                    repository.getRecognizedPersonName(result)

                // 11. 回到 Android 主執行緒顯示結果
                mainHandler.post {

                    if (isFinishing || isDestroyed) {
                        return@post
                    }

                    if (
                        result.isRecognized &&
                        !displayName.isNullOrBlank()
                    ) {


                        // 組員C：辨識成功後播放熟人稱呼
                        val recognizedPersonId = result.personId

                        if (!recognizedPersonId.isNullOrBlank()) {

                            // 經過10秒防重複播報控制
                            speechCoordinator.announcePerson(
                                personId = recognizedPersonId,
                                displayName = displayName
                            )
                        }

                        AlertDialog.Builder(
                            this@FamiliarPeopleActivity
                        )
                            .setTitle("熟人辨識成功")
                            .setMessage(
                                "辨識人物：$displayName\n" +
                                        "相似度：${"%.3f".format(result.similarity)}"
                            )
                            .setPositiveButton("確定", null)
                            .show()

                    } else {

                        AlertDialog.Builder(
                            this@FamiliarPeopleActivity
                        )
                            .setTitle("辨識結果")
                            .setMessage(
                                "未辨識出已註冊熟人。\n" +
                                        "相似度：${"%.3f".format(result.similarity)}"
                            )
                            .setPositiveButton("確定", null)
                            .show()
                    }
                }

            } catch (exception: Exception) {

                mainHandler.post {

                    if (!isFinishing && !isDestroyed) {

                        AlertDialog.Builder(
                            this@FamiliarPeopleActivity
                        )
                            .setTitle("辨識失敗")
                            .setMessage(
                                exception.message ?: "未知錯誤"
                            )
                            .setPositiveButton("確定", null)
                            .show()
                    }
                }

            } finally {

                // 釋放辨識模型與資料庫資源
                recognitionManager?.close()
                detector?.close()
                recognitionDbHelper?.close()

                // 釋放圖片記憶體
                normalizedBitmap?.recycle()
                croppedBitmap?.recycle()
                originalBitmap?.recycle()
            }
        }
    }



    /**
     * 組員C：計算兩組128維人臉特徵向量的
     * Cosine Similarity（餘弦相似度）。
     *
     * 回傳值通常介於 -1.0 到 1.0。
     * 越接近 1.0，代表向量方向越相似。
     */
    private fun calculateEmbeddingSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Double {

        require(first.size == 128 && second.size == 128) {
            "人臉特徵向量必須為128維"
        }

        var dotProduct = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0

        for (i in first.indices) {

            val a = first[i].toDouble()
            val b = second[i].toDouble()

            require(a.isFinite() && b.isFinite()) {
                "特徵向量包含無效數值"
            }

            dotProduct += a * b
            firstNorm += a * a
            secondNorm += b * b
        }

        // 避免零向量造成除以零
        require(firstNorm > 0.0 && secondNorm > 0.0) {
            "人臉特徵向量長度不可為零"
        }

        val similarity = dotProduct /
                (kotlin.math.sqrt(firstNorm) *
                        kotlin.math.sqrt(secondNorm))

        return similarity.coerceIn(-1.0, 1.0)
    }

    /**
     * 組員C：測試選取照片與已註冊人臉的相似度。
     *
     * 只讀取 SQLite，不新增、不修改、不刪除資料。
     */
    private fun testPhotoSimilarity(uri: Uri) {

        Toast.makeText(
            this,
            "正在計算人臉相似度...",
            Toast.LENGTH_SHORT
        ).show()

        registrationExecutor.execute {

            var detector: FaceDetector? = null
            var recognitionManager: FaceRecognitionManager? = null
            var testDatabaseHelper: FamiliarDatabaseHelper? = null

            var originalBitmap: Bitmap? = null
            var croppedBitmap: Bitmap? = null
            var normalizedBitmap: Bitmap? = null

            try {

                // 1. 讀取照片
                originalBitmap =
                    contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: error("無法讀取測試照片")

                // 2. 初始化 MediaPipe
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

                // 3. 人臉偵測
                val mpImage =
                    BitmapImageBuilder(originalBitmap).build()

                val detections =
                    detector.detect(mpImage).detections()

                check(detections.size == 1) {
                    "照片需要剛好一張人臉，目前偵測到 ${detections.size} 張"
                }

                // 4. 轉換偵測座標
                val box = detections[0].boundingBox()

                val detectedFace =
                    com.example.facerecognition.model.DetectedFace(
                        faceId = 1,
                        left = box.left.toFloat(),
                        top = box.top.toFloat(),
                        right = box.right.toFloat(),
                        bottom = box.bottom.toFloat()
                    )

                // 5. 裁切並縮放至 112 × 112
                val cropper = FaceCropper()

                croppedBitmap = cropper.cropFace(
                    originalBitmap,
                    detectedFace
                ) ?: error("人臉裁切失敗")

                normalizedBitmap =
                    cropper.resizeFace(croppedBitmap)

                // 6. 使用 MobileFaceNet 產生測試照片的向量
                recognitionManager = FaceRecognitionManager(
                    applicationContext
                )

                val testEmbedding =
                    recognitionManager.registerFace(normalizedBitmap)

                check(
                    testEmbedding.size == 128 &&
                            testEmbedding.all { it.isFinite() }
                ) {
                    "測試照片特徵向量無效"
                }

                // 7. 讀取正式 SQLite 的已註冊人物
                testDatabaseHelper = FamiliarDatabaseHelper(
                    applicationContext
                )

                val testDao =
                    FamiliarPersonDao(testDatabaseHelper)

                val registeredPeople =
                    testDao.getAllPersons()

                check(registeredPeople.isNotEmpty()) {
                    "SQLite 尚無已註冊熟人，請先註冊照片 A"
                }

                // 8. 逐一計算餘弦相似度
                val comparisons = registeredPeople.map { person ->

                    val similarity = calculateEmbeddingSimilarity(
                        testEmbedding,
                        person.embedding
                    )

                    person.getDisplayName() to similarity
                }

                // 9. 由高到低排序
                val sortedComparisons =
                    comparisons.sortedByDescending { it.second }

                val resultMessage = buildString {

                    appendLine("已註冊人數：${registeredPeople.size}")
                    appendLine()

                    sortedComparisons.forEach { (name, similarity) ->
                        appendLine(
                            "$name：${"%.4f".format(similarity)}"
                        )
                    }
                }

                // 10. 回到主執行緒顯示結果
                mainHandler.post {

                    if (!isFinishing && !isDestroyed) {

                        AlertDialog.Builder(
                            this@FamiliarPeopleActivity
                        )
                            .setTitle("人臉相似度測試結果")
                            .setMessage(resultMessage)
                            .setPositiveButton("確定", null)
                            .show()
                    }
                }

            } catch (exception: Exception) {

                mainHandler.post {

                    if (!isFinishing && !isDestroyed) {

                        AlertDialog.Builder(
                            this@FamiliarPeopleActivity
                        )
                            .setTitle("相似度測試失敗")
                            .setMessage(
                                exception.message ?: "未知錯誤"
                            )
                            .setPositiveButton("確定", null)
                            .show()
                    }
                }

            } finally {

                // 釋放模型、資料庫及 Bitmap
                recognitionManager?.close()
                detector?.close()
                testDatabaseHelper?.close()

                normalizedBitmap?.recycle()
                croppedBitmap?.recycle()
                originalBitmap?.recycle()
            }
        }
    }

    override fun onDestroy() {

        // 關閉 TTS，避免 Activity 結束後繼續播報
        if (::ttsManager.isInitialized) {
            ttsManager.release()
        }

        registrationExecutor.shutdown()
        databaseHelper.close()

        super.onDestroy()
    }


}
