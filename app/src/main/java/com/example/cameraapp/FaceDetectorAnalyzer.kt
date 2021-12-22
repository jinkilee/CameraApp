package com.example.cameraapp

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.mv.engine.FaceBox
import com.mv.engine.FaceDetector
import com.mv.engine.Live
import org.opencv.core.Mat
import java.nio.ByteBuffer

class FaceDetectorAnalyzer(private val listener: FaceroListner) : ImageAnalysis.Analyzer {

    var assetManager = MainActivity.ApplicationContext().resources.assets
    private var faceDetector: FaceDetector = FaceDetector()
    private var live: Live = Live()
//    private var ori: Int = 8    // big-gk
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

        var buffer: ByteBuffer? = null
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
        var m = Mat()
        var mc = Mat()

        val byteArray = makeNV21((image))

        val width = image.width
        val height = image.height

        // 640, 480 works
//        var boxes = faceDetector.detect(
//            byteArray,
//            width,
//            height,
//            ori,
//            m.nativeObjAddr,
//            mc.nativeObjAddr)
//
//        val nBoxes = boxes.size
//        val faceroResult = FaceBoxResult(boxes)
//
//        if(boxes.isNotEmpty()) {
//            Log.e(
//                "AAA", "boxes[0] confidence: " + "(" +
//                        boxes[0].left + ", " + boxes[0].top + ") (" +
//                        boxes[0].right + ", " + boxes[0].bottom + ") -> " + boxes[0].confidence
//            )
//
//            for(i in 0 until boxes.size) {
//                var antiProb = live.detect(byteArray, width, height, ori, boxes[i])
//                boxes[i].antiConf = antiProb
//                Log.d("AAA", "anti prob at $i = $antiProb")
//            }
//        }

        val faceBox1 = FaceBox(
            100, 200, 100+50, 200+50,
            0.900F,
            0, 0,
            0, 0,
            0, 0,
            0, 0,
            0, 0,
            0.970F,
            0.2222F,
        )
        val faceBox2 = FaceBox(
            200, 100, 200+300, 100+300,
            0.950F,
            0, 0,
            0, 0,
            0, 0,
            0, 0,
            0, 0,
            0.700F,
            0.4444F,
        )
        val bBoxes = listOf(faceBox1, faceBox2)
        val faceroResult = FaceBoxResult(bBoxes)

        listener(faceroResult)

        image.close()
    }
}


