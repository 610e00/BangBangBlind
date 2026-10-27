package com.example.facerecognition.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.facerecognition.model.DetectedFace


/**
 * FaceOverlayView
 *
 * 負責在畫面上繪製人臉框
 */
class FaceOverlayView(
    context: Context,
    attrs: AttributeSet?
) : View(context, attrs) {

    private val boxPaint = Paint().apply {

        color = Color.GREEN

        style = Paint.Style.STROKE

        strokeWidth = 8f

    }
    private var detectedFaces: List<DetectedFace> =
        emptyList()

    fun updateFaces(
        detectedFaces: List<DetectedFace>
    ) {

        this.detectedFaces =
            detectedFaces

        invalidate()

    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        for (detectedFace in detectedFaces) {

            canvas.drawRect(
                detectedFace.left,
                detectedFace.top,
                detectedFace.right,
                detectedFace.bottom,
                boxPaint
            )

        }

    }

}