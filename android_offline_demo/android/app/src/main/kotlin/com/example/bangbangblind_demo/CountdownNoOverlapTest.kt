package com.example.bangbangblind_demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Experimental crop refinement.
 *
 * Preserves the original Segmenter v4 algorithm.
 * Only removes horizontal overlap between two digit crops.
 *
 * This is for testing, not for safety decisions.
 */
class CountdownNoOverlapTest {

    fun segment(
        encodedRoi: ByteArray,
        shift: Int = 0
    ): CountdownSegmenterV4.Result {

        require(encodedRoi.isNotEmpty()) {
            "ROI image is empty"
        }

        require(shift in -2..2) {
            "Shift must be between -2 and 2"
        }

        val original = CountdownSegmenterV4().segment(encodedRoi)

        // Single-digit results remain unchanged.
        if (original.digitCount != 2 ||
            original.boxesXyxy.size != 2
        ) {
            return original
        }

        val left = original.boxesXyxy[0]
        val right = original.boxesXyxy[1]

        require(left.size == 4 && right.size == 4) {
            "Invalid digit boxes"
        }

        // No horizontal overlap: preserve original result.
        if (left[2] <= right[0]) {
            return original
        }

        val bitmap = BitmapFactory.decodeByteArray(
            encodedRoi,
            0,
            encodedRoi.size
        ) ?: error("Unable to decode ROI")

        try {
            val w = bitmap.width
            val h = bitmap.height

            require(
                left[0] >= 0 &&
                left[1] >= 0 &&
                right[0] >= 0 &&
                right[1] >= 0 &&
                left[2] <= w &&
                right[2] <= w &&
                left[3] <= h &&
                right[3] <= h &&
                left[0] < left[2] &&
                right[0] < right[2] &&
                left[1] < left[3] &&
                right[1] < right[3] &&
                left[0] <= right[0]
            ) {
                "Digit boxes outside ROI or unordered"
            }

            // Shared boundary inside the original overlap.
            val splitX = (left[2] + right[0]) / 2 + shift

            require(splitX in right[0]..left[2]) {
                "Split exceeds the overlap region"
            }

            require(
                splitX > left[0] &&
                splitX < right[2]
            ) {
                "Invalid split position"
            }

            val refinedBoxes = listOf(
                listOf(
                    left[0],
                    left[1],
                    splitX,
                    left[3]
                ),
                listOf(
                    splitX,
                    right[1],
                    right[2],
                    right[3]
                )
            )

            val refinedCrops = refinedBoxes.map { box ->
                val x1 = box[0]
                val y1 = box[1]
                val cropWidth = box[2] - x1
                val cropHeight = box[3] - y1

                require(cropWidth > 0 && cropHeight > 0) {
                    "Empty refined crop"
                }

                val crop = Bitmap.createBitmap(
                    bitmap,
                    x1,
                    y1,
                    cropWidth,
                    cropHeight
                )

                try {
                    ByteArrayOutputStream().use { stream ->
                        check(
                            crop.compress(
                                Bitmap.CompressFormat.PNG,
                                100,
                                stream
                            )
                        ) {
                            "Failed to encode crop"
                        }

                        stream.toByteArray()
                    }
                } finally {
                    if (crop !== bitmap) {
                        crop.recycle()
                    }
                }
            }

            return original.copy(
                strategy = original.strategy +
                    "+no_overlap_test",
                boxesXyxy = refinedBoxes,
                cropsPng = refinedCrops
            )
        } finally {
            bitmap.recycle()
        }
    }
}
