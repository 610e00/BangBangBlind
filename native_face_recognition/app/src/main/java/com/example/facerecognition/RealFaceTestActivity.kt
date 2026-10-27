
package com.example.facerecognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.facerecognition.crop.FaceCropper
import com.example.facerecognition.model.DetectedFace
import com.example.facerecognition.recognition.FaceSimilarityCalculator
import com.example.facerecognition.recognition.FaceRecognitionThreshold
import com.example.facerecognition.recognition.RecognitionResult
import com.example.facerecognition.recognition.MobileFaceNetManager
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.example.facerecognition.recognition.FaceMatcher
import com.example.facerecognition.recognition.FaceRecognitionManager

/**
 * RealFaceTestActivity
 *
 * 組員B：真人照片相似度測試程式
 *
 * 測試功能：
 * 1. 讀取四張真人照片
 * 2. 使用 MediaPipe 進行人臉偵測
 * 3. 使用組員A的 FaceCropper 裁切人臉
 * 4. 統一轉換為 112 × 112 Bitmap
 * 5. 使用 MobileFaceNet 提取 128 維特徵向量
 * 6. 使用 Cosine Similarity 比較六組照片
 * 7. 驗證交換模型輸入順序後的向量一致性
 *
 * 注意：
 * 目前只有兩個人物、四張照片。
 * 此程式用於功能驗證，尚不能代表正式辨識率。
 */
class RealFaceTestActivity : AppCompatActivity() {

    /**
     * 測試照片清單
     *
     * A1、A2：同一個人
     * B1、B2：另一個人
     *
     * 所有照片必須放在 app/src/main/assets。
     */
    private val photoNames = listOf(
        "person_a_1.jpg",
        "person_a_2.jpg",
        "person_b_1.jpg",
        "person_b_2.jpg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 建立畫面文字區域
        val resultText = TextView(this).apply {
            textSize = 16f
            setPadding(30, 60, 30, 30)
            text = "真人照片相似度測試中..."
        }

        setContentView(resultText)

        // 儲存模型管理物件，方便測試結束後釋放資源
        var detector: FaceDetector? = null
        var modelManager: MobileFaceNetManager? = null

        // 儲存四張完成裁切與縮放的人臉 Bitmap
        val faceBitmaps = mutableMapOf<String, Bitmap>()

        try {

            /**
             * 第一部分：初始化 MediaPipe 人臉偵測器
             *
             * 使用組員A原本的人臉偵測模型。
             */

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
                this,
                detectorOptions
            )

            // 使用組員A提供的裁切工具
            val faceCropper = FaceCropper()

            /**
             * 第二部分：依序處理四張照片
             *
             * 原始照片
             * → MediaPipe 人臉偵測
             * → Bounding Box
             * → Face Crop
             * → 112 × 112 Bitmap
             */

            for (photoName in photoNames) {

                // 從 assets 讀取照片並轉換成 Bitmap
                val originalBitmap = assets.open(photoName).use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: error("無法讀取照片：$photoName")

                try {

                    // 將 Bitmap 轉換成 MediaPipe 使用的 MPImage
                    val mpImage = BitmapImageBuilder(
                        originalBitmap
                    ).build()

                    // 執行人臉偵測
                    val detections = detector
                        .detect(mpImage)
                        .detections()

                    Log.d(
                        "RealFaceTest",
                        "$photoName Faces found: ${detections.size}"
                    )

                    // 每張照片必須只偵測到一張主要人臉
                    require(detections.size == 1) {
                        "$photoName 偵測到 ${detections.size} 張人臉，" +
                                "請確認照片只有一張清晰人臉"
                    }

                    // 取得偵測到的人臉 Bounding Box
                    val box = detections[0].boundingBox()

                    // 轉換成組員A建立的 DetectedFace 格式
                    val detectedFace = DetectedFace(
                        faceId = 0,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom
                    )

                    // 根據 Bounding Box 裁切人臉
                    val croppedFace = faceCropper.cropFace(
                        originalBitmap,
                        detectedFace
                    ) ?: error("$photoName 人臉裁切失敗")

                    /**
                     * 將裁切後的人臉縮放成 112 × 112。
                     *
                     * 若產生新的 Bitmap，需釋放原裁切圖片。
                     * 若回傳相同 Bitmap，則不重複釋放。
                     */
                    val normalizedFace = try {
                        faceCropper.resizeFace(croppedFace)
                    } catch (exception: Exception) {
                        croppedFace.recycle()
                        throw exception
                    }

                    if (normalizedFace !== croppedFace) {
                        croppedFace.recycle()
                    }

                    // 儲存標準化後的 Bitmap
                    faceBitmaps[photoName] = normalizedFace

                    Log.d(
                        "RealFaceTest",
                        "$photoName Normalized: " +
                                "${normalizedFace.width} x ${normalizedFace.height}"
                    )

                } finally {

                    // 原始照片處理完成後釋放記憶體
                    originalBitmap.recycle()
                }
            }

            /**
             * 第三部分：初始化 MobileFaceNet
             *
             * 模型規格：
             * Input 0：img1 [1, 3, 112, 112]
             * Input 1：img2 [1, 3, 112, 112]
             *
             * Output 0：embeddings [2, 128]
             */

            modelManager = MobileFaceNetManager(this)

            // 建立餘弦相似度計算工具
            val similarityCalculator = FaceSimilarityCalculator()

            // 組員B：建立門檻判斷工具
            // 目前 0.80f 是測試用門檻，尚未完成正式校準
            val recognitionThreshold = FaceRecognitionThreshold(0.80f)

            /**
             * 第四部分：建立六組照片比較
             *
             * 同一人：
             * 1. A1 vs A2
             * 2. B1 vs B2
             *
             * 不同人：
             * 3. A1 vs B1
             * 4. A2 vs B2
             * 5. A1 vs B2
             * 6. A2 vs B1
             */

            val comparisons = listOf(

                // 同一人 A
                Triple(
                    "person_a_1.jpg",
                    "person_a_2.jpg",
                    "同一人 A"
                ),

                // 同一人 B
                Triple(
                    "person_b_1.jpg",
                    "person_b_2.jpg",
                    "同一人 B"
                ),

                // 不同人 A1 與 B1
                Triple(
                    "person_a_1.jpg",
                    "person_b_1.jpg",
                    "不同人 A1/B1"
                ),

                // 不同人 A2 與 B2
                Triple(
                    "person_a_2.jpg",
                    "person_b_2.jpg",
                    "不同人 A2/B2"
                ),

                // 新增：不同人 A1 與 B2
                Triple(
                    "person_a_1.jpg",
                    "person_b_2.jpg",
                    "不同人 A1/B2"
                ),

                // 新增：不同人 A2 與 B1
                Triple(
                    "person_a_2.jpg",
                    "person_b_1.jpg",
                    "不同人 A2/B1"
                )
            )

            // 建立測試報告文字
            val report = StringBuilder()

            report.appendLine("真人照片相似度測試")
            report.appendLine()

            /**
             * 第五部分：依序執行六組照片比較
             */

            for ((firstName, secondName, label) in comparisons) {

                // 讀取已完成前處理的兩張人臉
                val firstFace = faceBitmaps.getValue(firstName)
                val secondFace = faceBitmaps.getValue(secondName)

                // 執行 MobileFaceNet 特徵向量提取
                val embeddings = modelManager.extractEmbeddings(
                    firstFace,
                    secondFace
                )

                // 確認輸出為兩組 128 維特徵向量
                check(
                    embeddings.size == 2 &&
                            embeddings.all { it.size == 128 }
                ) {
                    "模型輸出的特徵向量維度不正確"
                }

                // 計算兩組特徵向量的 Cosine Similarity
                val similarity =
                    similarityCalculator.calculateSimilarity(
                        embeddings[0],
                        embeddings[1]
                    )


                /**
                 * 組員B：Similarity Threshold 判斷
                 *
                 * 當 Cosine Similarity >= 0.80 時：
                 * 判定為「通過測試門檻」
                 *
                 * 當 Cosine Similarity < 0.80 時：
                 * 判定為「未通過測試門檻」
                 *
                 * 注意：
                 * 這是初步實驗結果，並非正式身分認證。
                 */

                // 判斷相似度是否達到測試門檻
                val isMatch = recognitionThreshold.isMatch(similarity)


                /**
                 * 組員B：RecognitionResult 資料格式測試
                 *
                 * 將原本分散的資料整理成一個物件：
                 * 1. faceId：目前為測試用編號
                 * 2. personId：通過門檻時使用測試人物代碼
                 * 3. similarity：兩張照片的相似度
                 * 4. isRecognized：是否通過門檻
                 *
                 * 注意：
                 * 這裡的 personId 來自已知測試照片標籤，
                 * 並不是系統自動搜尋人物資料庫的結果。
                 */

                // 測試資料：已知同一人 A 或 B 的標籤
                val testPersonId = when (label) {
                    "同一人 A" -> "person_a"
                    "同一人 B" -> "person_b"
                    else -> null
                }

                // 只有通過門檻且具有測試人物標籤時，
                // 才建立「已辨識」的測試結果。
                val recognized = isMatch && testPersonId != null

                // 建立辨識結果物件
                val recognitionResult = RecognitionResult(
                    faceId = 0,
                    personId = if (recognized) testPersonId else null,
                    similarity = similarity,
                    isRecognized = recognized
                )

                // 輸出完整資料，確認各欄位內容正確
                Log.d(
                    "RealFaceTest",
                    "RecognitionResult：$label → $recognitionResult"
                )


                // 將 Boolean 轉換成容易閱讀的中文
                val matchResult = if (isMatch) {
                    "通過測試門檻"
                } else {
                    "未通過測試門檻"
                }

                // 將判斷結果輸出到 Logcat
                Log.d(
                    "RealFaceTest",
                    "$label：相似度 = $similarity，" +
                            "門檻 = ${recognitionThreshold.getThreshold()}，" +
                            "結果 = $matchResult"
                )


                // 在 Logcat 輸出比較結果
                Log.d(
                    "RealFaceTest",
                    "$label ($firstName vs $secondName): " +
                            "Cosine Similarity = $similarity"
                )

                // 將結果加入測試畫面
                report.appendLine(label)
                report.appendLine("相似度：$similarity")
                report.appendLine()
            }

            /**
             * 第六部分：交換輸入順序測試
             *
             * 正向：
             * img1 = A1、img2 = A2
             *
             * 反向：
             * img1 = A2、img2 = A1
             *
             * 檢查相同照片在不同輸入位置時，
             * 輸出向量的方向是否一致。
             */

            val a1 = faceBitmaps.getValue("person_a_1.jpg")
            val a2 = faceBitmaps.getValue("person_a_2.jpg")

            // 正向輸入
            val forward = modelManager.extractEmbeddings(
                a1,
                a2
            )

            // 反向輸入
            val reversed = modelManager.extractEmbeddings(
                a2,
                a1
            )

            // 比較 A1 放在不同輸入位置時的特徵向量
            val a1Consistency =
                similarityCalculator.calculateSimilarity(
                    forward[0],
                    reversed[1]
                )

            // 比較 A2 放在不同輸入位置時的特徵向量
            val a2Consistency =
                similarityCalculator.calculateSimilarity(
                    forward[1],
                    reversed[0]
                )

            Log.d(
                "RealFaceTest",
                "交換順序 A1 向量一致性：$a1Consistency"
            )

            Log.d(
                "RealFaceTest",
                "交換順序 A2 向量一致性：$a2Consistency"
            )

            report.appendLine("交換輸入順序測試")
            report.appendLine("A1 一致性：$a1Consistency")
            report.appendLine("A2 一致性：$a2Consistency")

            /**
             * 第七部分：完成測試
             */


            /**
             * 第七部分：自動人物匹配測試
             *
             * 註冊照片：
             * A1 → person_a
             * B1 → person_b
             *
             * 待辨識照片：
             * A2、B2
             *
             * 不使用測試照片名稱決定辨識身分，
             * 而是交由 FaceMatcher 比較特徵向量。
             */

            // 建立自動人物匹配工具
            val faceMatcher = FaceMatcher(
                recognitionThreshold = recognitionThreshold
            )

            // 取得兩張註冊照片
            val registeredA = faceBitmaps.getValue("person_a_1.jpg")
            val registeredB = faceBitmaps.getValue("person_b_1.jpg")

            /**
             * 目前 MobileFaceNet 需要兩張圖片同時輸入。
             *
             * 使用同一張圖片輸入兩個位置，
             * 提取第一組 128 維向量作為註冊特徵。
             *
             * 這是目前模型的測試用擷取方式，
             * 後續仍要驗證不同輸入位置的數值一致性。
             */
            val aEmbedding = modelManager.extractEmbeddings(
                registeredA,
                registeredA
            )[0]

            val bEmbedding = modelManager.extractEmbeddings(
                registeredB,
                registeredB
            )[0]

            // 建立測試用人物特徵資料庫（只存在記憶體中）
            val registeredEmbeddings = mapOf(
                "person_a" to aEmbedding,
                "person_b" to bEmbedding
            )

            report.appendLine()
            report.appendLine("自動人物匹配測試")

            // 測試 A2 與 B2
            val queryPhotos = listOf(
                "person_a_2.jpg",
                "person_b_2.jpg"
            )

            for (queryName in queryPhotos) {

                val queryFace = faceBitmaps.getValue(queryName)

                // 待辨識人臉的特徵向量
                val queryEmbedding = modelManager.extractEmbeddings(
                    queryFace,
                    queryFace
                )[0]

                // 自動搜尋最相似的已註冊人物
                val result = faceMatcher.match(
                    faceId = 0,
                    queryEmbedding = queryEmbedding,
                    registeredEmbeddings = registeredEmbeddings
                )

                Log.d(
                    "RealFaceTest",
                    "自動匹配 $queryName → $result"
                )

                report.appendLine("$queryName")
                report.appendLine("人物：${result.personId ?: "未知人物"}")
                report.appendLine("相似度：${result.similarity}")
                report.appendLine("通過門檻：${result.isRecognized}")
                report.appendLine()
            }



            /**
             * 第八部分：未知人物拒絕測試
             *
             * 測試情境：
             * 只註冊 person_a，不註冊 person_b。
             *
             * 預期：
             * A2 → person_a
             * B2 → 未知人物
             *
             * 用來驗證 FaceMatcher 是否能拒絕
             * 未通過 Similarity Threshold 的人物。
             */

            // 建立只有人物 A 的測試資料庫
            val singlePersonDatabase = mapOf(
                "person_a" to aEmbedding
            )

            // 取得 A2 與 B2 的人臉圖片
            val knownFace = faceBitmaps.getValue("person_a_2.jpg")
            val unknownFace = faceBitmaps.getValue("person_b_2.jpg")

            // 提取 A2 的特徵向量
            val knownEmbedding = modelManager.extractEmbeddings(
                knownFace,
                knownFace
            )[0]

            // 提取 B2 的特徵向量
            val unknownEmbedding = modelManager.extractEmbeddings(
                unknownFace,
                unknownFace
            )[0]

            // A2：應辨識為 person_a
            val knownResult = faceMatcher.match(
                faceId = 0,
                queryEmbedding = knownEmbedding,
                registeredEmbeddings = singlePersonDatabase
            )

            // B2：應判定為未知人物
            val unknownResult = faceMatcher.match(
                faceId = 1,
                queryEmbedding = unknownEmbedding,
                registeredEmbeddings = singlePersonDatabase
            )

            // 檢查已註冊人物的辨識結果
            check(
                knownResult.isRecognized &&
                        knownResult.personId == "person_a"
            ) {
                "已知人物測試失敗"
            }

            // 檢查未註冊人物是否被拒絕
            check(
                !unknownResult.isRecognized &&
                        unknownResult.personId == null
            ) {
                "未知人物拒絕測試失敗"
            }

            // 顯示測試結果
            Log.d(
                "RealFaceTest",
                "已知人物測試 A2 → $knownResult"
            )

            Log.d(
                "RealFaceTest",
                "未知人物測試 B2 → $unknownResult"
            )

            Log.d(
                "RealFaceTest",
                "未知人物拒絕測試成功"
            )

            report.appendLine()
            report.appendLine("未知人物拒絕測試")
            report.appendLine("A2：${knownResult.personId ?: "未知人物"}")
            report.appendLine("B2：${unknownResult.personId ?: "未知人物"}")



            /**
             * 第九部分：空資料庫測試
             *
             * 測試情境：
             * 系統目前完全沒有註冊人物。
             *
             * 預期：
             * 不論輸入誰的人臉，
             * 都應回傳未知人物。
             */

            // 使用 A2 作為待辨識人臉
            val emptyDatabaseQuery = faceBitmaps.getValue(
                "person_a_2.jpg"
            )

            // 提取待辨識照片的特徵向量
            val emptyDatabaseEmbedding =
                modelManager.extractEmbeddings(
                    emptyDatabaseQuery,
                    emptyDatabaseQuery
                )[0]

            // 傳入空的註冊資料庫
            val emptyDatabaseResult = faceMatcher.match(
                faceId = 2,
                queryEmbedding = emptyDatabaseEmbedding,
                registeredEmbeddings = emptyMap()
            )

            // 確認系統沒有錯誤辨識人物
            check(
                !emptyDatabaseResult.isRecognized &&
                        emptyDatabaseResult.personId == null
            ) {
                "空資料庫測試失敗"
            }

            // 輸出測試結果
            Log.d(
                "RealFaceTest",
                "空資料庫測試 → $emptyDatabaseResult"
            )

            Log.d(
                "RealFaceTest",
                "空資料庫測試成功"
            )

            report.appendLine()
            report.appendLine("空資料庫測試")
            report.appendLine(
                "辨識結果：${emptyDatabaseResult.personId ?: "未知人物"}"
            )



            /**
             * 第十部分：FaceRecognitionManager 統一介面測試
             *
             * 測試組員C未來可以使用的辨識介面。
             *
             * 註冊：
             * A1 → person_a
             * B1 → person_b
             *
             * 辨識：
             * A2 → 預期 person_a
             * B2 → 預期 person_b
             *
             * 不再直接呼叫 MobileFaceNetManager
             * 或 FaceMatcher，而是統一透過
             * FaceRecognitionManager 操作。
             */

// 建立統一辨識管理器
            val recognitionManager = FaceRecognitionManager(
                context = this,
                threshold = 0.80f
            )

            try {

                // 取得兩張已完成裁切的人臉照片
                val registerFaceA =
                    faceBitmaps.getValue("person_a_1.jpg")

                val registerFaceB =
                    faceBitmaps.getValue("person_b_1.jpg")

                /**
                 * 第一階段：註冊人物
                 *
                 * registerFace() 只產生特徵向量，
                 * 不會將人物寫入永久資料庫。
                 */

                val embeddingA = recognitionManager.registerFace(
                    registerFaceA
                )

                val embeddingB = recognitionManager.registerFace(
                    registerFaceB
                )

                // 檢查註冊特徵維度
                check(embeddingA.size == 128) {
                    "人物 A 註冊特徵向量維度錯誤"
                }

                check(embeddingB.size == 128) {
                    "人物 B 註冊特徵向量維度錯誤"
                }

                // 建立暫存於記憶體的人物特徵資料
                val registeredFaces = mapOf(
                    "person_a" to embeddingA,
                    "person_b" to embeddingB
                )

                Log.d(
                    "RealFaceTest",
                    "FaceRecognitionManager：註冊 A、B 成功"
                )

                /**
                 * 第二階段：辨識人物 A
                 *
                 * 使用 A2 作為查詢圖片。
                 * 不預先傳入 A 的身分。
                 */

                val resultA = recognitionManager.recognizeFace(
                    faceId = 10,
                    faceBitmap = faceBitmaps.getValue(
                        "person_a_2.jpg"
                    ),
                    registeredEmbeddings = registeredFaces
                )

                Log.d(
                    "RealFaceTest",
                    "統一介面辨識 A2 → $resultA"
                )

                /**
                 * 第三階段：辨識人物 B
                 */

                val resultB = recognitionManager.recognizeFace(
                    faceId = 11,
                    faceBitmap = faceBitmaps.getValue(
                        "person_b_2.jpg"
                    ),
                    registeredEmbeddings = registeredFaces
                )

                Log.d(
                    "RealFaceTest",
                    "統一介面辨識 B2 → $resultB"
                )

                /**
                 * 第四階段：檢查辨識結果
                 */

                check(
                    resultA.isRecognized &&
                            resultA.personId == "person_a" &&
                            resultA.faceId == 10
                ) {
                    "FaceRecognitionManager 人物 A 辨識失敗"
                }

                check(
                    resultB.isRecognized &&
                            resultB.personId == "person_b" &&
                            resultB.faceId == 11
                ) {
                    "FaceRecognitionManager 人物 B 辨識失敗"
                }

                Log.d(
                    "RealFaceTest",
                    "FaceRecognitionManager 統一介面測試成功"
                )

                // 將測試結果加入模擬器畫面
                report.appendLine()
                report.appendLine("統一辨識介面測試")
                report.appendLine("A2 → ${resultA.personId}")
                report.appendLine("相似度：${resultA.similarity}")
                report.appendLine("B2 → ${resultB.personId}")
                report.appendLine("相似度：${resultB.similarity}")

            } finally {

                // 不論測試成功或失敗，都關閉模型
                recognitionManager.close()
            }


            resultText.text = report.toString()

            Log.d(
                "RealFaceTest",
                "六組真人照片相似度測試完成"
            )

        } catch (exception: Exception) {

            /**
             * 測試失敗處理
             *
             * 若照片無法讀取、偵測失敗，
             * 或模型推論出現問題，
             * 將錯誤訊息顯示在畫面與 Logcat。
             */

            resultText.text =
                "真人照片測試失敗：${exception.message}"

            Log.e(
                "RealFaceTest",
                "真人照片測試失敗",
                exception
            )

        } finally {

            /**
             * 第八部分：釋放資源
             *
             * 不論測試成功或失敗，
             * 都釋放 Bitmap、MobileFaceNet
             * 與 MediaPipe 使用的資源。
             */

            faceBitmaps.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            modelManager?.close()
            detector?.close()
        }
    }
}
