package com.example.cameraapp

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
import androidx.annotation.RequiresApi
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

//class FaceBoxResult {
//    var bBoxes: List<FaceBox> = emptyList()
//
//    constructor(bBoxes: List<FaceBox>) {
//        for(box in bBoxes) {
//            this.bBoxes += box
//        }
//        Log.d("AAA", "number of Boxes -> ${this.bBoxes.size}")
//    }
//}

typealias FaceroListner = (faceroResult: FaceBoxResult) -> Unit

class MainActivity : AppCompatActivity() {

    init {
        instance = this
        val isInitialized = OpenCVLoader.initDebug()
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var overlay: Bitmap

    private var faceDetector: FaceDetector = FaceDetector()
    private var live: Live = Live()

    @RequiresApi(Build.VERSION_CODES.O)
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

        outputDirectory = getOutputDirectory()
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

    private fun scale(x: Float):Float {
        val getWidth = 1080F
        val imageWidth = 480F
        val ScaleFactor:Float = getWidth / imageWidth
        Log.d("CCC1", "imagePixel = $x")
        Log.d("CCC1", "ScaleFactor = $ScaleFactor")
        return x * ScaleFactor
    }

    private fun translateX(x: Float): Float {
        // FIXME: isImageFiliped=true if using front-camera
        val isImageFliped:Boolean = false

        val imageAspectRatio = 1080F / 1920F
        val getHeight: Float = 1573F        // FIXME
        val getWidth: Float = 1080F         // FIXME
        val postScaleWidthOffset:Float = (getHeight as Float * imageAspectRatio - getWidth) / 2
        var translatedX: Float = scale(x) - postScaleWidthOffset

        Log.d("AAA1", "x = $x")
        Log.d("AAA1", "scale(x) = " + scale(x))
        Log.d("AAA1", "postScaleWidthOffset = $postScaleWidthOffset")

        if(isImageFliped) {
            translatedX = getWidth - translatedX
        }
        return translatedX
    }

    private fun translateY(y: Float): Float {
        val imageAspectRatio = 1080F / 1920F
        val getHeight:Float = 1573F         // FIXME
        val getWidth:Float = 1080F          // FIXME
        val postScaleHeightOffset = (getWidth / imageAspectRatio - getHeight) / 2
        val translatedY: Float = scale(y) - postScaleHeightOffset
        return translatedY
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

            // paint setting value
            val textStroke = 8F
            val rectStroke = 7F
            val textBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val successColor = Color.parseColor("#64E312")
            val failureColor = Color.parseColor("#E91111")

            // define Text paint
            val successTextPaint = Paint().apply {
                isAntiAlias = true
                textSize = 20F
                typeface = textBold
                color = successColor
                strokeWidth = textStroke
            }

            val failureTextPaint = Paint().apply {
                isAntiAlias = true
                textSize = 20F
                typeface = textBold
                color = failureColor
                strokeWidth = textStroke
            }

            // define Rect paint
            val successRectPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = successColor
                strokeWidth = rectStroke
            }

            val failureRectPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = failureColor
                strokeWidth = rectStroke
            }

            var rectPaint: Paint
            var textPaint: Paint

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceDetectorAnalyzer { faceroResult ->
                        val bitmap = textureView.bitmap ?:return@FaceDetectorAnalyzer
                        overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

                        for(rect in faceroResult.bBoxes) {
                            var canvas = Canvas(overlay)

                            // prepare some FaceBox info
                            val left = rect.left.toFloat()
                            val top = rect.top.toFloat()
                            val right = rect.right.toFloat()
                            val bottom = rect.bottom.toFloat()
                            val anti = rect.antiConf
                            val confidence = rect.confidence
                            val boxString = "Face=$confidence Anti=$anti"
                            val textLeft = left
                            val textTop = (top)
                            val width = right - left
                            val height = bottom - top
                            Log.d("AAA", "confidence=$confidence anti=$anti")

                            val translatedX = translateX(left)
                            val translatedY = translateY(top)

                            Log.d("AAA",
                                "(" + left + "," + top + ")"
                                + " -> " +
                                "(" + translatedX + "," + translatedY + ")"
                            )
                            // check if face is valid
                            // "anti > 0.85F" means it is a real face
                            if(confidence > 0.80 && anti > 0.85F) {
                                rectPaint = successRectPaint
                                textPaint = successTextPaint
                            }
                            else {
                                rectPaint = failureRectPaint
                                textPaint = failureTextPaint
                            }

                            canvas.drawRect(left, top, left + 10F, top + 10F, rectPaint)
                            canvas.drawRect(translatedX, translatedY, translatedX + width, translatedY + height, rectPaint)
                            canvas.drawText(boxString, textLeft, textTop, textPaint)

                            overlay?.let { Canvas(it) }?.apply {
                                canvas
                            }
                        }

                        runOnUiThread {
                            imageView.setImageBitmap(overlay)
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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

    override fun onDestroy() {
        super.onDestroy()
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
}