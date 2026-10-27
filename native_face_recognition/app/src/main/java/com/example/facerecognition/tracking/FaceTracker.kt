package com.example.facerecognition.tracking

import com.example.facerecognition.model.DetectedFace
import kotlin.math.sqrt

/**
 * FaceTracker
 *
 * 負責追蹤同一張人臉
 *
 * 組員A：
 * - 避免同一張臉每一幀都被當成新的人臉
 * - 提供人臉ID管理
 *
 * 原理：
 * 比較兩張人臉中心點距離
 *
 * 距離 < 100：
 * 視為同一張臉
 *
 * 距離 >= 100：
 * 建立新的人臉ID
 *
 * 流程：
 * Face Detection
 * ↓
 * Face Tracker
 * ↓
 * Face Crop
 * ↓
 * Face Recognition
 */
class FaceTracker {

    /**
     * 目前追蹤中的人臉
     */
    private val trackedFaces =
        mutableListOf<DetectedFace>()

    /**
     * 下一個可用的人臉ID
     */
    private var nextFaceId = 1

    /**
     * 根據座標追蹤人臉
     */
    fun trackFace(
        detectedFace: DetectedFace
    ): Int {

        for (trackedFace in trackedFaces) {

            val distance =
                calculateDistance(
                    trackedFace,
                    detectedFace
                )

            /**
             * 距離夠近
             * 視為同一張臉
             */
            if (distance < 100f) {

                return trackedFace.faceId

            }

        }

        /**
         * 新的人臉
         */
        val newFaceId = nextFaceId

        nextFaceId++

        trackedFaces.add(
            detectedFace.copy(
                faceId = newFaceId
            )
        )

        return newFaceId

    }

    /**
     * 計算兩張臉中心點距離
     */
    private fun calculateDistance(
        face1: DetectedFace,
        face2: DetectedFace
    ): Float {

        val centerX1 =
            (face1.left + face1.right) / 2

        val centerY1 =
            (face1.top + face1.bottom) / 2

        val centerX2 =
            (face2.left + face2.right) / 2

        val centerY2 =
            (face2.top + face2.bottom) / 2

        return sqrt(
            (
                    (centerX1 - centerX2) *
                            (centerX1 - centerX2)
                    ) +
                    (
                            (centerY1 - centerY2) *
                                    (centerY1 - centerY2)
                            )
        )

    }

    /**
     * 重置追蹤器
     */
    fun resetTracker() {

        trackedFaces.clear()

        nextFaceId = 1

    }

}