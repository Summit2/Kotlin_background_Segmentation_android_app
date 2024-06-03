package com.ttv.segmentdemo

import android.content.Context
import android.content.res.TypedArray
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.ttv.segment.TTVException
import com.ttv.segment.TTVSeg
import io.fotoapparat.Fotoapparat
import io.fotoapparat.parameter.Resolution
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.front
import io.fotoapparat.util.FrameProcessor
import io.fotoapparat.view.CameraView
import java.lang.Exception
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.*
import android.view.Surface
import android.widget.Button
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private val permissionsDelegate = PermissionsDelegate(this)
    private var hasPermission = false

    private var appCtx: Context? = null
    private var licenseValid = false
    private var humanSegInited = false
    private var cameraView: CameraView? = null
    private var imageView: ImageView? = null

    private var frontFotoapparat: Fotoapparat? = null
    private var bgDrawableId = android.R.color.transparent

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            val i: Int = msg.what
            if (i == 0) {
                imageView!!.setImageBitmap(msg.obj as Bitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val buttonRed = findViewById<Button>(R.id.button_red)
        val buttonBlue = findViewById<Button>(R.id.button_blue)
        val buttonGreen = findViewById<Button>(R.id.button_green)
        val buttonBack = findViewById<Button>(R.id.button_back)

        buttonRed.setOnClickListener { setRedBackground() }
        buttonBlue.setOnClickListener { setBlueBackground() }
        buttonGreen.setOnClickListener { setGreenBackground() }
        buttonBack.setOnClickListener { setOriginalBackground() }

        appCtx = applicationContext
        cameraView = findViewById<View>(R.id.camera_view) as CameraView
        imageView = findViewById<View>(R.id.image_view) as ImageView

        TTVSeg.createInstance(this)

        hasPermission = permissionsDelegate.hasPermissions()
        if (hasPermission) {
            cameraView!!.visibility = View.VISIBLE
        } else {
            permissionsDelegate.requestPermissions()
        }

        frontFotoapparat = Fotoapparat.with(this)
            .into(cameraView!!)
            .lensPosition(front())
            .frameProcessor(SampleFrameProcessor())
            .previewResolution { Resolution(480,320) }
            .build()

        val ret = TTVSeg.getInstance().setLicenseInfo("")
        if(ret == 0) {
            licenseValid = true
            init()
        }
    }

    private fun setRedBackground() {
        bgDrawableId = R.drawable.one
    }

    private fun setGreenBackground() {
        bgDrawableId = R.drawable.two
    }

    private fun setBlueBackground() {
        bgDrawableId = R.drawable.three
    }

    private fun setOriginalBackground() {
        bgDrawableId = android.R.color.transparent
    }

    private fun init() {
        if (!licenseValid) {
            return
        }

        try {
            if (TTVSeg.getInstance().create(appCtx, 0, 0, 0) == 0) {
                humanSegInited = true
                return
            }
        } catch (e: TTVException) {
            e.printStackTrace()
        }
    }


    override fun onStart() {
        super.onStart()
        if (hasPermission) {
            frontFotoapparat!!.start()
        }
    }


    override fun onStop() {
        super.onStop()
        if (hasPermission) {
            try {
                frontFotoapparat!!.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionsDelegate.hasPermissions() && !hasPermission) {
            hasPermission = true
            cameraView!!.visibility = View.VISIBLE
            frontFotoapparat!!.start()
        } else {
            permissionsDelegate.requestPermissions()
        }
    }

    inner class SampleFrameProcessor : FrameProcessor {
        private var ttvSeg: TTVSeg? = null
        private val bgColor = intArrayOf(255, 255, 255)

        init {
            ttvSeg = TTVSeg.getInstance()
        }

        override fun invoke(frame: Frame) {
            if (!humanSegInited) {
                return
            }

            val bitmap = convertYuvToBitmap(frame, 270)

            val iArr = IntArray(1)

            val bgBitmap: Bitmap? = BitmapFactory.decodeResource(resources, bgDrawableId)

            val segment: Bitmap = ttvSeg!!.process(
                frame.image,
                frame.size.width,
                frame.size.height,
                frame.rotation,
                1,
                bgColor,
                bgBitmap,
                iArr
            )



            if (bgDrawableId == android.R.color.transparent) {
                sendMessage(0, bitmap)
            } else {
                sendMessage(0, segment)
            }
        }

        private fun sendMessage(w: Int, o: Any) {
            val message = Message()
            message.what = w
            message.obj = o
            mHandler.sendMessage(message)
        }

        private fun convertYuvToBitmap(frame: Frame, rotationDegrees: Int): Bitmap {
            val yuvImage = YuvImage(
                frame.image,
                ImageFormat.NV21,
                frame.size.width,
                frame.size.height,
                null
            )

            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, frame.size.width, frame.size.height), 100, outputStream)

            val jpegByteArray = outputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)

            return rotateBitmap(bitmap, rotationDegrees)
        }

        private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
            val matrix = Matrix().apply {
                postRotate(degrees.toFloat())
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
