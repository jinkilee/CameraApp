package com.example.cameraapp

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
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
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

typealias LumaListener = (luma: Double) -> Unit
typealias FaceroListner = (nBoxes: Int) -> Unit

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
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { nBoxes ->
                        Log.d("AAA", "Number of Bboxes found: $nBoxes")
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

    private class LuminosityAnalyzer(private val listener: FaceroListner) : ImageAnalysis.Analyzer {

        var assetManager = MainActivity.ApplicationContext().resources.assets
        private var faceDetector: FaceDetector = FaceDetector()
        private var live: Live = Live()
        // private var ori: Int = 8    // big-gk
        private var ori: Int = 6    // gk

        init {
            //val isInitialized = OpenCVLoader.initDebug()

            var retFaceDetector = faceDetector.loadModel(assetManager)
            Log.d("AAA", "faceDetector load: $retFaceDetector")

            var retLive = live.loadModel(assetManager)
            Log.d("AAA", "live load: $retLive")
        }
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        private fun makeByteArray(image: ImageProxy): ByteArray {
            val yBuffer = image.planes[0].buffer
            val vBuffer = image.planes[2].buffer

            yBuffer.rewind()
            //vBuffer.rewind()

            var ySize = 0
            var uSize = 0
            var vSize = 0

            ySize = yBuffer.remaining()
            vSize = vBuffer.remaining()

            val totalSize = ySize + uSize + vSize
            val nv21 = ByteArray(totalSize)
            Log.d("AAA", "ByteArray size = " + nv21.size)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)

            return nv21
        }

        private fun makeNV21(image: ImageProxy): ByteArray {
            val planes = image.planes
            val crop = image.cropRect

            val format = image.format
            val width = crop.width()
            val height = crop.height()
            var channelOffset = 0
            var outputStride = 1

            val mData = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
            val mRowData = ByteArray(planes[0].rowStride)

            var buffer:ByteBuffer? = null
            for(i in 0 until planes.size) {
                when(i) {
                    0 -> {
                        channelOffset = 0
                        outputStride = 1
                    }
                    1 -> {
                        channelOffset = width * height + 1
                        outputStride = 2
                    }
                    2 -> {
                        channelOffset = width * height
                        outputStride = 2
                    }
                }

                buffer = planes[i].buffer
                val rowStride = planes[i].rowStride
                val pixelStide = planes[i].pixelStride

                val shift = if(i == 0) 0 else 1
                val w = width shr shift
                val h = height shr shift
                buffer.position(rowStride * (crop.top shr shift) + pixelStide * (crop.left shr shift))
                for(row in 0 until h) {
                    var length: Int
                    if(pixelStide == 1 && outputStride == 1) {
                        length = w
                        buffer.get(mData, channelOffset, length)
                        channelOffset += length
                    }
                    else {
                        length = (w - 1) * pixelStide + 1
                        buffer.get(mRowData, 0, length)
                        for(col in 0 until w) {
                            mData[channelOffset] = mRowData[col * pixelStide]
                            channelOffset += outputStride
                        }
                    }
                    if(row < h - 1) {
                        buffer.position(buffer.position() + rowStride - length)
                    }
                }
            }

            buffer?.clear()

            return mData
        }

        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            var m = Mat()
            var mc = Mat()

            val byteArray = makeNV21((image))

            val width = image.width
            val height = image.height

            // 640, 480 works
            var boxes = faceDetector.detect(
                byteArray,
                width,
                height,
                ori,
                m.nativeObjAddr,
                mc.nativeObjAddr)

            val nBoxes = boxes.size

            if(boxes.isNotEmpty()) {
                Log.e(
                    "AAA", "boxes[0] confidence: " + "(" +
                            boxes[0].left + ", " + boxes[0].top + ") (" +
                            boxes[0].right + ", " + boxes[0].bottom + ")"
                )

                var antiProb = live.detect(
                    byteArray,
                    width,
                    height,
                    ori,
                    boxes[0]
                )
                Log.d("AAA", "anti prob = $antiProb")
            }

            listener(nBoxes)
            //listener(luma)

            image.close()
        }
    }
}