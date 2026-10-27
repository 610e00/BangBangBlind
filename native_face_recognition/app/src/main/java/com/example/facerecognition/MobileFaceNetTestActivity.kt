
package com.example.facerecognition

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.facerecognition.recognition.MobileFaceNetManager
import com.example.facerecognition.recognition.FaceSimilarityCalculator
import kotlin.math.abs

/**
 * MobileFaceNetTestActivity
 *
 * 組員B：人臉辨識核心模組測試程式
 *
 * 測試項目：
 * 1. 載入 MobileFaceNet TensorFlow Lite 模型
 * 2. 將 Bitmap 轉換為模型輸入格式
 * 3. 執行模型推論，取得兩組 128 維特徵向量
 * 4. 驗證 Cosine Similarity 計算公式
 *
 * 注意：
 * 目前使用灰色、白色測試圖片，以及人工設定的數學向量。
 * 測試成功不代表已完成真人辨識或辨識率測試。
 */
class MobileFaceNetTestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 建立畫面文字，顯示測試結果
        val resultText = TextView(this).apply {
            textSize = 20f
            setPadding(30, 60, 30, 30)
            text = "MobileFaceNet 測試中..."
        }

        setContentView(resultText)

        // 保留資源參考，方便測試結束後釋放
        var modelManager: MobileFaceNetManager? = null
        var firstBitmap: Bitmap? = null
        var secondBitmap: Bitmap? = null

        try {

            /**
             * 第一部分：載入 MobileFaceNet 模型
             */

            modelManager = MobileFaceNetManager(this)

            Log.d(
                "MobileFaceNet",
                "MobileFaceNet 模型載入成功，開始推論測試"
            )

            /**
             * 第二部分：建立兩張測試圖片
             *
             * 圖片一：灰色
             * 圖片二：白色
             *
             * 尺寸皆為 112 × 112。
             * 這兩張不是人臉圖片，僅用來確認推論流程。
             */

            firstBitmap = Bitmap.createBitmap(
                112,
                112,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(Color.GRAY)
            }

            secondBitmap = Bitmap.createBitmap(
                112,
                112,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(Color.WHITE)
            }

            /**
             * 第三部分：執行 MobileFaceNet 特徵提取
             *
             * MobileFaceNetManager 會：
             * 1. 將 Bitmap 轉換為 Float32
             * 2. 將像素正規化至 0～1
             * 3. 依 NCHW 格式排列
             * 4. 傳入 img1、img2
             * 5. 取得 embeddings [2, 128]
             */

            val embeddings = modelManager.extractEmbeddings(
                firstBitmap,
                secondBitmap
            )

            /**
             * 第四部分：檢查特徵向量
             */

            // 確認模型輸出兩組 128 維向量
            check(
                embeddings.size == 2 &&
                        embeddings.all { it.size == 128 }
            ) {
                "特徵向量維度不符合預期"
            }

            // 確認每一個數值都不是 NaN 或 Infinity
            check(
                embeddings.all { embedding ->
                    embedding.all { feature ->
                        feature.isFinite()
                    }
                }
            ) {
                "特徵向量包含無效數值"
            }

            // 將特徵向量資訊輸出到 Logcat
            Log.d(
                "MobileFaceNet",
                "Embedding 1 size: ${embeddings[0].size}"
            )

            Log.d(
                "MobileFaceNet",
                "Embedding 2 size: ${embeddings[1].size}"
            )

            Log.d(
                "MobileFaceNet",
                "Embedding 1 前5個值: ${embeddings[0].take(5)}"
            )

            Log.d(
                "MobileFaceNet",
                "Embedding 2 前5個值: ${embeddings[1].take(5)}"
            )

            Log.d(
                "MobileFaceNet",
                "MobileFaceNet 特徵向量推論測試成功"
            )

            /**
             * 第五部分：Cosine Similarity 數學測試
             *
             * 測試一：相同向量，預期結果 1.0
             * 測試二：垂直向量，預期結果 0.0
             * 測試三：相反向量，預期結果 -1.0
             *
             * 此部分只測試相似度計算公式，
             * 不代表真人身分辨識。
             */

            val similarityCalculator = FaceSimilarityCalculator()

            // 第一組基準向量
            val vectorA = floatArrayOf(1f, 0f)

            // 與基準向量相同
            val vectorSame = floatArrayOf(1f, 0f)

            // 與基準向量垂直
            val vectorDifferent = floatArrayOf(0f, 1f)

            // 與基準向量方向相反
            val vectorOpposite = floatArrayOf(-1f, 0f)

            // 計算三種測試情境的相似度
            val sameSimilarity = similarityCalculator.calculateSimilarity(
                vectorA,
                vectorSame
            )

            val differentSimilarity = similarityCalculator.calculateSimilarity(
                vectorA,
                vectorDifferent
            )

            val oppositeSimilarity = similarityCalculator.calculateSimilarity(
                vectorA,
                vectorOpposite
            )

            /**
             * 第六部分：檢查數學測試結果
             *
             * 使用容許誤差 0.0001，
             * 避免浮點數精度造成比較問題。
             */

            check(abs(sameSimilarity - 1f) < 0.0001f) {
                "相同向量的 Cosine Similarity 測試失敗"
            }

            check(abs(differentSimilarity) < 0.0001f) {
                "垂直向量的 Cosine Similarity 測試失敗"
            }

            check(abs(oppositeSimilarity + 1f) < 0.0001f) {
                "相反向量的 Cosine Similarity 測試失敗"
            }

            // 將相似度測試結果輸出到 Logcat
            Log.d(
                "MobileFaceNet",
                "Cosine Same: $sameSimilarity"
            )

            Log.d(
                "MobileFaceNet",
                "Cosine Different: $differentSimilarity"
            )

            Log.d(
                "MobileFaceNet",
                "Cosine Opposite: $oppositeSimilarity"
            )

            Log.d(
                "MobileFaceNet",
                "Cosine Similarity 數學測試成功"
            )

            /**
             * 第七部分：更新模擬器畫面
             */

            resultText.text = """
                組員B：核心模組測試成功！

                MobileFaceNet：載入成功

                Embedding 1：${embeddings[0].size} 維
                Embedding 2：${embeddings[1].size} 維

                Cosine Same：$sameSimilarity
                Cosine Different：$differentSimilarity
                Cosine Opposite：$oppositeSimilarity

                詳細紀錄請查看 Logcat。
            """.trimIndent()

        } catch (exception: Exception) {

            /**
             * 第八部分：錯誤處理
             */

            resultText.text =
                "組員B測試失敗：${exception.message}"

            Log.e(
                "MobileFaceNet",
                "組員B核心模組測試失敗",
                exception
            )

        } finally {

            /**
             * 第九部分：釋放測試資源
             *
             * 不論測試成功或失敗，
             * 都釋放 Bitmap 及 TensorFlow Lite Interpreter。
             */

            firstBitmap?.recycle()
            secondBitmap?.recycle()
            modelManager?.close()
        }
    }
}
