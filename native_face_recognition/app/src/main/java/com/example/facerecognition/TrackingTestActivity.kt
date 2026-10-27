package com.example.facerecognition

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.facerecognition.model.DetectedFace
import com.example.facerecognition.tracking.FaceTracker

/**
 * Face Tracking 驗證程式
 *
 * 組員A測試：
 * 驗證同一張臉不會被重複建立ID
 */
class TrackingTestActivity : AppCompatActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val tracker =
            FaceTracker()

        /**
         * 第一張人臉
         */
        val id1 =
            tracker.trackFace(
                DetectedFace(
                    0,
                    100f,
                    100f,
                    200f,
                    200f
                )
            )

        /**
         * 第二張人臉
         * 座標略微移動
         */
        val id2 =
            tracker.trackFace(
                DetectedFace(
                    0,
                    110f,
                    105f,
                    210f,
                    205f
                )
            )

        Log.d(
            "FaceRecognition",
            "Tracking Test: $id1 -> $id2"
        )

        val textView =
            TextView(this)

        textView.text =
            "Tracking Test: $id1 -> $id2"

        setContentView(textView)
    }
}