
package com.example.facerecognition.recognition

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * MobileFaceNetManager
 *
 * 組員B：人臉辨識核心模組
 *
 * 目前功能：
 * 1. 從 assets 載入 MobileFaceNet 模型
 * 2. 建立 TensorFlow Lite Interpreter
 * 3. 查看模型實際的輸入、輸出規格
 *
 * 注意：
 * 目前尚未執行人臉特徵向量提取。
 */
class MobileFaceNetManager(context: Context) {

    // 用來執行 TensorFlow Lite 模型
    private val interpreter: Interpreter

    init {
        // 載入 assets 內的 MobileFaceNet 模型
        val modelBuffer = loadModelFile(
            context,
            "mobile_facenet.tflite"
        )

        // 建立模型執行器
        interpreter = Interpreter(modelBuffer)

        Log.d(
            "MobileFaceNet",
            "MobileFaceNet 模型載入成功"
        )

        // 印出模型實際的輸入規格
        for (index in 0 until interpreter.inputTensorCount) {
            val tensor = interpreter.getInputTensor(index)

            Log.d(
                "MobileFaceNet",
                "Input $index: ${tensor.name()}, " +
                        "shape=${tensor.shape().contentToString()}, " +
                        "type=${tensor.dataType()}"
            )
        }

        // 印出模型實際的輸出規格
        for (index in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(index)

            Log.d(
                "MobileFaceNet",
                "Output $index: ${tensor.name()}, " +
                        "shape=${tensor.shape().contentToString()}, " +
                        "type=${tensor.dataType()}"
            )
        }
    }

    /**
     * 從 assets 讀取 TFLite 模型。
     *
     * 使用記憶體映射方式讀取模型檔案，
     * 供 TensorFlow Lite Interpreter 使用。
     */
    private fun loadModelFile(
        context: Context,
        fileName: String
    ): MappedByteBuffer {

        val fileDescriptor = context.assets.openFd(fileName)

        return fileDescriptor.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }
    }

    /**
     * 使用完畢後釋放模型資源。
     */

    /**
     * 使用兩張人臉圖片執行 MobileFaceNet 推論。
     *
     * @param firstFace 第一張 112 × 112 人臉 Bitmap
     * @param secondFace 第二張 112 × 112 人臉 Bitmap
     *
     * @return 兩組 128 維特徵向量
     *
     * 注意：
     * 此模型有 img1、img2 兩個輸入，
     * 並且輸出 embeddings [2, 128]。
     *
     * 目前只是特徵提取，還沒有做人臉身分比對。
     */
    fun extractEmbeddings(
        firstFace: android.graphics.Bitmap,
        secondFace: android.graphics.Bitmap
    ): Array<FloatArray> {

        // 建立圖片前處理工具
        val preprocessor = FaceImagePreprocessor()

        // 將兩張 Bitmap 轉換成 NCHW Float32 格式
        val firstInput = preprocessor.bitmapToFloatArray(firstFace)
        val secondInput = preprocessor.bitmapToFloatArray(secondFace)

        // 建立模型的兩組輸入
        // 順序依照之前 Logcat 驗證的 img1、img2
        val inputs = arrayOf<Any>(
            firstInput,
            secondInput
        )

        // 建立輸出容器：[2, 128]
        val embeddings = Array(2) {
            FloatArray(128)
        }

        // Output Tensor 0 對應 embeddings
        val outputs = mutableMapOf<Int, Any>(
            0 to embeddings
        )

        // 執行 TensorFlow Lite 推論
        interpreter.runForMultipleInputsOutputs(
            inputs,
            outputs
        )

        // 檢查輸出是否包含非有限數值
        for (embedding in embeddings) {
            require(embedding.all { it.isFinite() }) {
                "MobileFaceNet 輸出含有無效數值"
            }
        }

        // 回傳兩張臉的特徵向量
        return embeddings
    }

    fun close() {
        interpreter.close()
    }
}
