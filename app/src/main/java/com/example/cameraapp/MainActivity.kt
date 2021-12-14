package com.example.cameraapp

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.mv.engine.FaceDetector
import com.mv.engine.Live
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import com.google.android.gms.vision.Frame
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.graphics.YuvImage
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    init {
        instance = this
        val isInitialized = OpenCVLoader.initDebug()
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

//    private var faceDetector: FaceDetector = FaceDetector()
//    private var live: Live = Live()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AAA", "nativeLib: " + getApplicationInfo().nativeLibraryDir)
        setContentView(R.layout.activity_main)

        if(allPermissionGranted()) {
            Log.d("AAA", "permission granted")
            startCamera()
        }
        else {
            Log.d("AAA", "no permission granted")
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        for (item in REQUIRED_PERMISSIONS) {
            Log.d("AAA", "item -> $item") // item -> android.permission.CAMERA
        }

        camera_capture_button.setOnClickListener {
            takePhoto()
        }

        // outputDirectory = File("/mnt/sdcard/facero")
        outputDirectory = getOutputDirectory()
        Log.d("AAA", outputDirectory.toString())

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d("AAA", "onRequestPermissionsResult called")
        if(requestCode == REQUEST_CODE_PERMISSIONS) {
            if(allPermissionGranted()) {
                startCamera()
            }
            else {
                Toast.makeText(this,
                    "Permissions not granted by the user",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if(mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun takePhoto() {
        Log.d("AAA", "takePhoto called")
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        Log.d("AAA", "photoFile" + photoFile.toString())

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        Log.d("AAA", "outputOptions" + outputOptions.toString())

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("AAA", "Photo capture failed: ${exception.message}", exception)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d("AAA", msg)
                }
            }
        )
    }

    private fun startCamera() {
        Log.d("AAA", "startCamera called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1080, 1920))
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d("AAA", "Average luminosity: $luma")
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            }
            catch(exc: Exception) {
                Log.e("AAA", "camera finding fained", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun allPermissionGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStart() {
        super.onStart()
        Log.d("AAA", "onStart executed")
    }

    override fun onResume() {
        super.onResume()
        Log.d("AAA", "onResume executed")
    }

    override fun onPause() {
        super.onPause()
        Log.d("AAA", "onPause executed")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AAA", "onDestroy executed")

        cameraExecutor.shutdown()
        Log.d("AAA", "cameraExecutor shutdown")
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        lateinit var instance: MainActivity
        fun ApplicationContext(): Context {
            return instance.applicationContext
        }
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        var assetManager = MainActivity.ApplicationContext().resources.assets
//        private var faceDetector: FaceDetector = FaceDetector()
//        private var live: Live = Live()
        private var ori: Int = 1

        init {
            //val isInitialized = OpenCVLoader.initDebug()

//            var retFaceDetector = faceDetector.loadModel(assetManager)
//            Log.d("AAA", "faceDetector load: $retFaceDetector")
//
//            var retLive= live.loadModel(assetManager)
//            Log.d("AAA", "live load: $retLive")
        }
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        private fun makeFrame1(image: ImageProxy): Frame {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            Log.d("AAA", "Buffer set")

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            Log.d("AAA", "Buffer remain")

            val nv21 = ByteArray(ySize + uSize + vSize)
            Log.d("AAA", "nv21 set")

            yBuffer.get(nv21, 0, ySize)
            uBuffer.get(nv21, ySize, vSize)
            vBuffer.get(nv21, ySize + vSize, uSize)
            Log.d("AAA", "nv21 filling")

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, 480, 640, null)
            Log.d("AAA", "YuvImage set")

            val frame = Frame.Builder()
                .setImageData(ByteBuffer.wrap(yuvImage.yuvData), 480, 640, ImageFormat.NV21)
                .build()
            Log.d("AAA", "frame built on makeFrame1")

            return frame
        }

        private fun makeFrame2(image: ImageProxy): Frame {
            val buffer = image.planes[0].buffer
            val imageByte = ByteArray(buffer.capacity())
            Log.d("AAA", "imageByte prepared")

            buffer.rewind();
            buffer.get(imageByte)
            Log.d("AAA", "buffer setting")

            val frame = Frame.Builder()
                .setImageData(buffer, 480, 640, ImageFormat.NV21)
                .build()
            Log.d("AAA", "frame built on makeFrame2")

            return frame
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            var m = Mat()
            var mc = Mat()

            if(image.image != null) {
                val image = image.image
                Log.d("AAA", "image is not null")
            }
            else {
                Log.d("AAA", "image is null")
            }

//            val frame = makeFrame1(image)
            val frame = makeFrame2(image)


//            var boxes = faceDetector.detect(
//                frame.grayscaleImageData.array(),
//                480,
//                640,
//                ori,
//                m.nativeObjAddr,
//                mc.nativeObjAddr)
//            Log.e(
//                "AAA", "boxes[0] confidence: " + "(" +
//                        boxes[0].left + ", " + boxes[0].top + ") (" +
//                        boxes[0].right + ", " + boxes[0].bottom + ")"
//            )
//
//
//            var antiProb = live.detect(imageBytes, 480, 640, 1, boxes[0])
//            Log.e("AAA", "anti probability: $antiProb")

            listener(luma)

            image.close()
        }
    }
}