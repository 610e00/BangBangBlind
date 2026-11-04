package com.example.bangbangblind_demo

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

class DigitCnnNative(private val context: Context) {

    private val assetPath =
        "flutter_assets/assets/models/digit_recognizer_v3_e3.tflite"

    fun testZeroInput(): FloatArray {
        return predict(FloatArray(1 * 3 * 64 * 64))
    }

    fun predict(inputData: FloatArray): FloatArray {
        require(inputData.size == 1 * 3 * 64 * 64) {
            "CNN requires 12288 Float32 values, got ${inputData.size}"
        }
        require(inputData.all { it.isFinite() }) {
            "CNN input contains NaN or Infinity"
        }

        // Copy the Flutter asset to a real filesystem path.
        val modelFile = java.io.File(
            context.cacheDir,
            "digit_recognizer_v3_e3.tflite"
        )

        context.assets.open(assetPath).use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val options = CompiledModel.Options(Accelerator.CPU)
        val model = CompiledModel.create(
            modelFile.absolutePath,
            options
        )

        try {
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()

            try {
                // NCHW: [1, 3, 64, 64]
                inputs[0].writeFloat(inputData)
                model.run(inputs, outputs)

                val logits = outputs[0].readFloat()

                check(logits.size == 10) {
                    "Expected 10 logits, got ${logits.size}"
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
