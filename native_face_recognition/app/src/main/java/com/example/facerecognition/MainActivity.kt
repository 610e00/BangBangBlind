package com.example.facerecognition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.facerecognition.detection.FaceDetectorManager
import com.example.facerecognition.overlay.FaceOverlayView
import com.example.facerecognition.model.DetectedFace

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private val faceDetectorManager =
        FaceDetectorManager()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        faceDetectorManager.initializeDetector(this)
        previewView = findViewById(R.id.previewView)
        faceOverlayView = findViewById(R.id.faceOverlayView)


        // 檢查相機權限
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )

        } else {
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview =
                Preview.Builder().build()

            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()

            val imageAnalysis =
                androidx.camera.core.ImageAnalysis.Builder()
                    .build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->
                val detectedFaces =
                    faceDetectorManager.detectFace(
                        imageProxy
                    )

                faceOverlayView.updateFaces(
                    detectedFaces
                )

                Log.d(
                    "FaceRecognition",
                    "Detected faces: ${detectedFaces.size}"
                )

                imageProxy.close()
            }

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(this))
    }
}