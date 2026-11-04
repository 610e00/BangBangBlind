package com.example.bangbangblind_demo

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Kotlin port of traffic/countdown_segmenter_v4.py.
 *
 * Input: encoded PNG/JPG bytes of an already YOLO-cropped countdown ROI.
 * Output: digit_count, strategy, boxes_xyxy (exclusive right/bottom), and PNG digit crops.
 * No CNN inference is performed here.
 *
 * Python round() uses ties-to-even. Math.rint() preserves that for positive pixel sizes.
 * The Python implementation intentionally does not update height_fraction on merge;
 * this port preserves that behavior for golden-reference parity.
 */
class CountdownSegmenterV4 {
    data class Result(
        val digitCount: Int,
        val strategy: String,
        val boxesXyxy: List<List<Int>>,
        val foregroundAspectRatio: Double,
        val cropsPng: List<ByteArray>
    )

    private data class Box(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        val width: Int get() = x2 - x1
        val height: Int get() = y2 - y1
        fun asList(): List<Int> = listOf(x1, y1, x2, y2)
    }

    private data class Candidate(
        var x1: Int, var y1: Int, var x2: Int, var y2: Int,
        var width: Int, var height: Int, var mass: Int,
        var relativeCenter: Double,
        var widthFraction: Double,
        // Deliberately preserved across merges, matching Python v4.
        var heightFraction: Double
    ) {
        fun duplicate() = copy()
    }

    companion object {
        private const val TARGET_MINIMUM_BRIGHTNESS = 110
        private const val TARGET_MAXIMUM_BRIGHTNESS = 230
        private const val LED_MIN_SATURATION = 45
        private const val WHITE_MIN_BRIGHTNESS = 195
        private const val SINGLE_DIGIT_RATIO = 0.62

        private fun pyRound(value: Double): Int = Math.rint(value).toInt()

        /** Prevent an error from being mistaken for a zero-second countdown. */
        private fun requireOpenCv() {
            check(OpenCVLoader.initLocal()) {
                "OpenCV Android native library failed to initialize"
            }
        }
    }

    fun segment(encodedRoi: ByteArray): Result {
        require(encodedRoi.isNotEmpty()) { "Countdown ROI bytes are empty" }
        requireOpenCv()
        val encoded = MatOfByte(*encodedRoi)
        val roi = try {
            Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR)
        } finally {
            encoded.release()
        }
        try {
            require(!roi.empty() && roi.type() == CvType.CV_8UC3) {
                "Expected uint8 3-channel BGR image"
            }
            return segmentBgr(roi)
        } finally {
            roi.release()
        }
    }

    private fun segmentBgr(roi: Mat): Result {
        val h = roi.rows()
        val w = roi.cols()
        require(h > 0 && w > 0) { "Empty countdown ROI" }

        val mask = buildLedMask(roi)
        try {
            val pixels = ByteArray(w * h)
            check(mask.get(0, 0, pixels) == pixels.size) {
                "Failed to read LED foreground mask"
            }
            val activeBox = foregroundBox(pixels, w, h)
                ?: error("No LED foreground")
            val ratio = activeBox.width.toDouble() / max(1, activeBox.height)
            val digitCount = if (ratio < SINGLE_DIGIT_RATIO) 1 else 2
            val candidates = buildXRunCandidates(pixels, w, h)

            val boxes: List<Box>
            val strategy: String
            if (digitCount == 1) {
                boxes = listOf(padBox(activeBox, w, h))
                strategy = "single_AR"
            } else {
                val selected = chooseTwoDigitCandidates(candidates, w)
                if (selected != null) {
                    boxes = selected.map { padBox(candidateBox(it), w, h) }
                    strategy = "direct_x_run_pair"
                } else {
                    val splitX = pyRound((activeBox.x1 + activeBox.x2) / 2.0)
                    val left = foregroundBox(
                        pixels, w, h,
                        activeBox.x1, activeBox.y1, splitX, activeBox.y2
                    ) ?: error("Unable to resolve two digit candidates")
                    val right = foregroundBox(
                        pixels, w, h,
                        splitX, activeBox.y1, activeBox.x2, activeBox.y2
                    ) ?: error("Unable to resolve two digit candidates")
                    boxes = listOf(padBox(left, w, h), padBox(right, w, h))
                    strategy = "midpoint_fallback"
                }
            }

            val ordered = boxes.sortedBy { it.x1 }
            val pngCrops = ordered.map { box ->
                check(box.width > 0 && box.height > 0) { "Empty crop" }
                val submat = roi.submat(Rect(box.x1, box.y1, box.width, box.height))
                val buffer = MatOfByte()
                try {
                    check(Imgcodecs.imencode(".png", submat, buffer)) {
                        "Failed to encode digit crop"
                    }
                    buffer.toArray()
                } finally {
                    buffer.release()
                    submat.release()
                }
            }
            return Result(digitCount, strategy, ordered.map { it.asList() }, ratio, pngCrops)
        } finally {
            mask.release()
        }
    }

    private fun buildLedMask(roi: Mat): Mat {
        val hsv = Mat()
        val s = Mat()
        val v = Mat()
        val otsuResult = Mat()
        var mask: Mat? = null
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        try {
            Imgproc.cvtColor(roi, hsv, Imgproc.COLOR_BGR2HSV)
            Core.extractChannel(hsv, s, 1)
            Core.extractChannel(hsv, v, 2)
            val otsu = Imgproc.threshold(
                v, otsuResult, 0.0, 255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
            )
            val threshold = max(
                TARGET_MINIMUM_BRIGHTNESS,
                min(TARGET_MAXIMUM_BRIGHTNESS, otsu.toInt())
            )
            val sData = ByteArray(roi.rows() * roi.cols())
            val vData = ByteArray(roi.rows() * roi.cols())
            check(s.get(0, 0, sData) == sData.size)
            check(v.get(0, 0, vData) == vData.size)
            val maskData = ByteArray(sData.size)
            val whiteThreshold = max(WHITE_MIN_BRIGHTNESS, threshold)
            for (i in maskData.indices) {
                val sat = sData[i].toInt() and 0xff
                val brightness = vData[i].toInt() and 0xff
                val colored = brightness >= threshold && sat >= LED_MIN_SATURATION
                val white = brightness >= whiteThreshold
                maskData[i] = if (colored || white) 0xff.toByte() else 0
            }
            val createdMask = Mat(roi.rows(), roi.cols(), CvType.CV_8UC1)
            mask = createdMask
            check(createdMask.put(0, 0, maskData) == maskData.size)
            Imgproc.morphologyEx(createdMask, createdMask, Imgproc.MORPH_OPEN, kernel)
            return createdMask
        } catch (e: Exception) {
            mask?.release()
            throw e
        } finally {
            kernel.release()
            otsuResult.release()
            v.release()
            s.release()
            hsv.release()
        }
    }

    /** Half-open ROI and half-open bbox coordinates, matching NumPy slicing. */
    private fun foregroundBox(
        mask: ByteArray, w: Int, h: Int,
        left: Int = 0, top: Int = 0, right: Int = w, bottom: Int = h
    ): Box? {
        require(left in 0..w && right in 0..w && top in 0..h && bottom in 0..h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in top until bottom) {
            for (x in left until right) {
                if (mask[y * w + x].toInt() != 0) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }
        return if (maxX < minX) null else Box(minX, minY, maxX + 1, maxY + 1)
    }

    private fun buildXRunCandidates(mask: ByteArray, w: Int, h: Int): List<Candidate> {
        val projection = IntArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y * w + x].toInt() != 0) projection[x]++
            }
        }
        val threshold = max(1, pyRound(h * 0.025))
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (x in 0 until w) {
            if (projection[x] >= threshold && start < 0) start = x
            if (projection[x] < threshold && start >= 0) {
                runs.add(start to x)
                start = -1
            }
        }
        if (start >= 0) runs.add(start to w)

        val out = mutableListOf<Candidate>()
        for ((x1, x2) in runs) {
            if (x2 <= x1) continue
            var y1 = h
            var y2 = -1
            var mass = 0
            for (y in 0 until h) {
                for (x in x1 until x2) {
                    if (mask[y * w + x].toInt() != 0) {
                        y1 = min(y1, y)
                        y2 = max(y2, y)
                        mass++
                    }
                }
            }
            if (mass == 0) continue
            val width = x2 - x1
            val height = y2 + 1 - y1
            out.add(
                Candidate(
                    x1, y1, x2, y2 + 1, width, height, mass,
                    (x1 + x2) / 2.0 / max(1, w),
                    width.toDouble() / max(1, w),
                    height.toDouble() / max(1, h)
                )
            )
        }
        return out
    }

    private fun isEdgeArtifact(c: Candidate): Boolean {
        val atEdge = c.relativeCenter < 0.14 || c.relativeCenter > 0.86
        val narrow = c.widthFraction < 0.11
        val tall = c.heightFraction > 0.55
        return atEdge && narrow && tall
    }

    private fun mergeCloseCandidates(candidates: List<Candidate>, roiWidth: Int): List<Candidate> {
        if (candidates.size <= 1) return candidates.map { it.duplicate() }
        val sorted = candidates.sortedBy { it.x1 }
        val maxGap = max(1, pyRound(roiWidth * 0.018))
        val merged = mutableListOf<Candidate>()
        var current = sorted[0].duplicate()
        for (candidate in sorted.drop(1)) {
            val gap = candidate.x1 - current.x2
            if (gap <= maxGap) {
                current.x2 = max(current.x2, candidate.x2)
                current.y1 = min(current.y1, candidate.y1)
                current.y2 = max(current.y2, candidate.y2)
                current.mass += candidate.mass
                current.width = current.x2 - current.x1
                current.height = current.y2 - current.y1
                current.relativeCenter = (current.x1 + current.x2) / 2.0 / roiWidth
                current.widthFraction = current.width.toDouble() / roiWidth
                // Keep heightFraction unchanged: the v4 Python code does not update it.
            } else {
                merged.add(current)
                current = candidate.duplicate()
            }
        }
        merged.add(current)
        return merged
    }

    private fun chooseTwoDigitCandidates(candidates: List<Candidate>, roiWidth: Int): List<Candidate>? {
        val valid = mergeCloseCandidates(candidates.filterNot { isEdgeArtifact(it) }, roiWidth)
        if (valid.size < 2) return null
        var best: List<Candidate>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in 0 until valid.size) {
            for (j in i + 1 until valid.size) {
                val left = valid[i]
                val right = valid[j]
                val distance = right.relativeCenter - left.relativeCenter
                if (distance < 0.12) continue
                val totalMass = left.mass + right.mass
                val pairCenter = (left.relativeCenter + right.relativeCenter) / 2.0
                val centerPenalty = abs(pairCenter - 0.5)
                val score = totalMass.toDouble() - centerPenalty * totalMass * 0.20
                // Python's stable sort prefers the first pair on tied scores.
                if (score > bestScore) {
                    bestScore = score
                    best = listOf(left, right)
                }
            }
        }
        return best
    }

    private fun candidateBox(c: Candidate) = Box(c.x1, c.y1, c.x2, c.y2)

    private fun padBox(box: Box, fullW: Int, fullH: Int): Box {
        val xPad = max(2, pyRound(fullW * 0.018))
        val yPad = max(2, pyRound(fullH * 0.030))
        return Box(
            max(0, box.x1 - xPad), max(0, box.y1 - yPad),
            min(fullW, box.x2 + xPad), min(fullH, box.y2 + yPad)
        )
    }
}
