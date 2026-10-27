package com.example.facerecognition.model


/**
 * DetectedFace
 *
 * 儲存偵測到的人臉資訊
 *
 * 組員A：
 * MediaPipe 偵測後產生
 *
 * 組員B：
 * 可利用人臉區域進行特徵提取
 */
data class DetectedFace(

    // 人臉追蹤編號
    val faceId: Int,

    // 人臉框左上角 X
    val left: Float,

    // 人臉框左上角 Y
    val top: Float,

    // 人臉框右下角 X
    val right: Float,

    // 人臉框右下角 Y
    val bottom: Float

)