package com.example.bangbangblind_demo

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

class DigitCnnFp16Native(private val context: Context) {

    private val assetPath =
        "flutter_assets/assets/models/digit_recognizer_v3_e3_fp16.tflite"

    private fun chwToHwc(chw: FloatArray): FloatArray {
        require(chw.size == 3 * 64 * 64) {
            "Expected 12288 values"
        }

        val plane = 64 * 64
        val hwc = FloatArray(chw.size)

        for (i in 0 until plane) {
            hwc[i * 3] = chw[i]
            hwc[i * 3 + 1] = chw[plane + i]
            hwc[i * 3 + 2] = chw[2 * plane + i]
        }

        return hwc
    }

    fun predict(inputChw: FloatArray): FloatArray {

        require(inputChw.size == 12288) {
            "CNN requires 12288 Float32 values"
        }

        require(inputChw.all { it.isFinite() }) {
            "CNN input contains NaN or Infinity"
        }

        val modelFile = java.io.File(
            context.cacheDir,
            "digit_recognizer_v3_e3_fp16.tflite"
        )

        context.assets.open(assetPath).use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val model = CompiledModel.create(
            modelFile.absolutePath,
            CompiledModel.Options(Accelerator.CPU)
        )

        try {
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()

            try {
                val hwcInput = chwToHwc(inputChw)

                // FP16 TFLite 接受 Float32 NHWC 輸入
                inputs[0].writeFloat(hwcInput)

                model.run(inputs, outputs)

                val logits = outputs[0].readFloat()

                check(logits.size == 10) {
                    "Expected 10 logits, got ${logits.size}"
                }

                check(logits.all { it.isFinite() }) {
                    "CNN output contains NaN or Infinity"
                }

                return logits

            } finally {
                inputs.forEach { it.close() }
                outputs.forEach { it.close() }
            }

        } finally {
            model.close()
        }
    }
}
