package com.example.bangbangblind_demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Android OpenCV implementation of Python D5 preprocessing.
 *
 * Input : encoded single-digit crop (PNG/JPEG)
 * Output: FloatArray in RGB CHW layout [3, 64, 64]
 */
class DigitD5OpenCv {

    fun preprocess(imageBytes: ByteArray): FloatArray {
        require(imageBytes.isNotEmpty()) {
            "Digit crop is empty"
        }

        require(imageBytes.size <= 10 * 1024 * 1024) {
            "Digit crop exceeds 10 MB"
        }

        check(OpenCVLoader.initLocal()) {
            "Failed to initialize OpenCV"
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            options
        ) ?: error("Unable to decode digit crop")

        val rgba = Mat()
        val bgr = Mat()
        val resized = Mat()
        val canvas = Mat.zeros(64, 64, CvType.CV_8UC3)
        val rgb = Mat()

        try {
            val width = bitmap.width
            val height = bitmap.height

            require(width > 0 && height > 0) {
                "Invalid crop dimensions"
            }

            // Bitmap is RGBA; Python expects BGR.
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(
                rgba,
                bgr,
                Imgproc.COLOR_RGBA2BGR
            )

            val scale = minOf(
                64.0 / width,
                64.0 / height
            )

            // Python round() uses ties-to-even.
            val newWidth = Math.rint(width * scale)
                .toInt()
                .coerceIn(1, 64)

            val newHeight = Math.rint(height * scale)
                .toInt()
                .coerceIn(1, 64)

            val interpolation = if (scale < 1.0) {
                Imgproc.INTER_AREA
            } else {
                Imgproc.INTER_LINEAR
            }

            Imgproc.resize(
                bgr,
                resized,
                Size(
                    newWidth.toDouble(),
                    newHeight.toDouble()
                ),
                0.0,
                0.0,
                interpolation
            )

            // Center the resized image on a black 64x64 canvas.
            val x0 = (64 - newWidth) / 2
            val y0 = (64 - newHeight) / 2

            val destination = canvas.submat(
                y0,
                y0 + newHeight,
                x0,
                x0 + newWidth
            )

            try {
                resized.copyTo(destination)
            } finally {
                destination.release()
            }

            // Python: cv2.cvtColor(BGR, RGB)
            Imgproc.cvtColor(
                canvas,
                rgb,
                Imgproc.COLOR_BGR2RGB
            )

            // Convert RGB uint8 HWC to normalized Float32 CHW.
            val pixels = ByteArray(64 * 64 * 3)
            val copied = rgb.get(0, 0, pixels)

            check(copied == pixels.size) {
                "Unexpected OpenCV pixel buffer size"
            }

            val planeSize = 64 * 64
            val tensor = FloatArray(3 * planeSize)

            for (i in 0 until planeSize) {
                val pixelOffset = i * 3

                tensor[i] =
                    (pixels[pixelOffset].toInt() and 0xFF) / 255.0f

                tensor[planeSize + i] =
                    (pixels[pixelOffset + 1].toInt() and 0xFF) / 255.0f

                tensor[2 * planeSize + i] =
                    (pixels[pixelOffset + 2].toInt() and 0xFF) / 255.0f
            }

            return tensor
        } finally {
            rgb.release()
            canvas.release()
            resized.release()
            bgr.release()
            rgba.release()
            bitmap.recycle()
        }
    }
}
