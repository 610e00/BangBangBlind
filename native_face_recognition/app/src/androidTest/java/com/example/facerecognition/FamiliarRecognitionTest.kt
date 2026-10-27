
package com.example.facerecognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.facerecognition.crop.FaceCropper
import com.example.facerecognition.database.FamiliarDatabaseHelper
import com.example.facerecognition.database.FamiliarPerson
import com.example.facerecognition.database.FamiliarPersonDao
import com.example.facerecognition.database.FamiliarRecognitionRepository
import com.example.facerecognition.model.DetectedFace
import com.example.facerecognition.recognition.FaceRecognitionManager
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 組員C：SQLite 與真人照片辨識整合測試
 *
 * 測試流程：
 * 1. 讀取 assets 真人照片
 * 2. MediaPipe 偵測人臉
 * 3. FaceCropper 裁切成 112 × 112
 * 4. FaceRecognitionManager 提取特徵向量
 * 5. SQLite 儲存熟人資料
 * 6. 讀取 SQLite 特徵向量進行辨識
 * 7. 取得熟人稱呼
 *
 * 注意：
 * 使用獨立的測試資料庫，
 * 不會刪除正式的 familiar_faces.db。
 */
@RunWith(AndroidJUnit4::class)
class FamiliarRecognitionTest {

    // 專門用於整合測試的資料庫
    private val testDatabaseName =
        "familiar_recognition_test.db"

    private lateinit var context: Context
    private lateinit var databaseHelper: FamiliarDatabaseHelper
    private lateinit var personDao: FamiliarPersonDao
    private lateinit var recognitionManager: FaceRecognitionManager
    private lateinit var repository: FamiliarRecognitionRepository
    private lateinit var detector: FaceDetector

    private val faceCropper = FaceCropper()

    @Before
    fun setUp() {

        // 取得 App 的 Context
        context = ApplicationProvider.getApplicationContext()

        // 清除測試專用資料庫
        context.deleteDatabase(testDatabaseName)

        // 初始化 SQLite
        databaseHelper = FamiliarDatabaseHelper(
            context,
            testDatabaseName
        )

        personDao = FamiliarPersonDao(databaseHelper)

        // 初始化組員B的辨識管理器
        recognitionManager = FaceRecognitionManager(
            context = context,
            threshold = 0.80f
        )

        // 初始化組員C的辨識資料整合類別
        repository = FamiliarRecognitionRepository(
            personDao = personDao,
            recognitionManager = recognitionManager
        )

        // 初始化組員A使用的 MediaPipe 人臉偵測器
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
            context,
            detectorOptions
        )
    }

    @After
    fun tearDown() {

        // 釋放模型與資料庫資源
        if (::detector.isInitialized) {
            detector.close()
        }

        if (::recognitionManager.isInitialized) {
            recognitionManager.close()
        }

        if (::databaseHelper.isInitialized) {
            databaseHelper.close()
        }

        // 只刪除整合測試資料庫
        if (::context.isInitialized) {
            context.deleteDatabase(testDatabaseName)
        }
    }

    /**
     * 讀取照片並裁切成 112 × 112。
     *
     * 完全沿用組員B已通過測試的流程。
     */
    private fun loadNormalizedFace(
        photoName: String
    ): Bitmap {

        // 從 assets 讀取照片
        val originalBitmap = context.assets.open(
            photoName
        ).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: error("無法讀取照片：$photoName")

        try {

            // 將 Bitmap 轉為 MediaPipe MPImage
            val mpImage = BitmapImageBuilder(
                originalBitmap
            ).build()

            // 執行人臉偵測
            val detections = detector
                .detect(mpImage)
                .detections()

            // 測試照片預期只有一張人臉
            require(detections.size == 1) {
                "$photoName 偵測到 ${detections.size} 張人臉"
            }

            // 取得人臉 Bounding Box
            val box = detections[0].boundingBox()

            // 轉成組員A的 DetectedFace
            val detectedFace = DetectedFace(
                faceId = 0,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )

            // 裁切人臉
            val croppedFace = faceCropper.cropFace(
                originalBitmap,
                detectedFace
            ) ?: error("$photoName 人臉裁切失敗")

            // 縮放為 112 × 112
            val normalizedFace = try {
                faceCropper.resizeFace(croppedFace)
            } catch (exception: Exception) {
                croppedFace.recycle()
                throw exception
            }

            // 若產生新 Bitmap，釋放舊裁切圖片
            if (normalizedFace !== croppedFace) {
                croppedFace.recycle()
            }

            return normalizedFace

        } finally {

            // 釋放原始照片 Bitmap
            originalBitmap.recycle()
        }
    }

    /**
     * 驗證：
     * 真人照片 → SQLite 註冊 → 辨識 → 查詢稱呼。
     */
    @Test
    fun testRecognitionWithSQLite() {

        // 載入四張真人照片
        val faceA1 = loadNormalizedFace("person_a_1.jpg")
        val faceA2 = loadNormalizedFace("person_a_2.jpg")
        val faceB1 = loadNormalizedFace("person_b_1.jpg")
        val faceB2 = loadNormalizedFace("person_b_2.jpg")

        try {

            /**
             * 第一階段：產生註冊特徵向量
             */

            val embeddingA = recognitionManager.registerFace(
                faceA1
            )

            val embeddingB = recognitionManager.registerFace(
                faceB1
            )

            /**
             * 第二階段：寫入 SQLite
             *
             * 程式中使用「媽媽」、「朋友」
             * 作為測試稱呼。
             */

            val personA = FamiliarPerson(
                personId = "person_a",
                name = "測試人物A",
                relationship = "媽媽",
                embedding = embeddingA
            )

            val personB = FamiliarPerson(
                personId = "person_b",
                name = "測試人物B",
                relationship = "朋友",
                embedding = embeddingB
            )

            assertTrue(personDao.insertPerson(personA))
            assertTrue(personDao.insertPerson(personB))

            // 確認資料庫確實有兩位熟人
            assertEquals(2, personDao.getAllPersons().size)

            /**
             * 第三階段：測試人物A
             *
             * 使用另一張 A2 照片進行辨識，
             * 不直接指定 A 的人物 ID。
             */

            val resultA = repository.recognize(
                faceId = 10,
                faceBitmap = faceA2
            )

            // 確認模型透過 SQLite 向量辨識出 A
            assertTrue(resultA.isRecognized)
            assertEquals("person_a", resultA.personId)

            // 從 SQLite 讀取語音播報稱呼
            val displayNameA =
                repository.getRecognizedPersonName(resultA)

            assertEquals("媽媽", displayNameA)

            /**
             * 第四階段：測試人物B
             */

            val resultB = repository.recognize(
                faceId = 11,
                faceBitmap = faceB2
            )

            assertTrue(resultB.isRecognized)
            assertEquals("person_b", resultB.personId)

            val displayNameB =
                repository.getRecognizedPersonName(resultB)

            assertEquals("朋友", displayNameB)

            // 顯示整合測試結果
            println("SQLite 與真人照片辨識整合測試成功")
            println("A2 辨識結果：$resultA")
            println("A2 熟人稱呼：$displayNameA")
            println("B2 辨識結果：$resultB")
            println("B2 熟人稱呼：$displayNameB")

        } finally {

            // 測試結束後釋放照片 Bitmap
            listOf(
                faceA1,
                faceA2,
                faceB1,
                faceB2
            ).forEach { bitmap ->

                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
}
