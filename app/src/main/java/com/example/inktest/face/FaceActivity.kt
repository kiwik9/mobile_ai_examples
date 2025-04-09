package com.example.inktest.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.inktest.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import java.io.IOException
import kotlin.math.max

class FaceActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private var mImageView: ImageView? = null
    private var mTextButton: Button? = null
    private var mFaceButton: Button? = null
    private var mSelectedImage: Bitmap? = null
    private var mGraphicOverlay: GraphicOverlay? = null

    // Max width (portrait mode)
    private var mImageMaxWidth: Int? = null

    // Max height (portrait mode)
    private var mImageMaxHeight: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)

        mImageView = findViewById(R.id.image_view)

        mTextButton = findViewById(R.id.button_text)
        mFaceButton = findViewById(R.id.button_face)

        mGraphicOverlay = findViewById(R.id.graphic_overlay)
        mTextButton?.setOnClickListener { runTextRecognition() }
        mFaceButton?.setOnClickListener { runFaceContourDetection() }

        val dropdown = findViewById<Spinner>(R.id.spinner)
        val items = arrayOf("Test Image 1 (Text)", "Test Image 2 (Face)")
        val adapter = ArrayAdapter(
            this, android.R.layout
                .simple_spinner_dropdown_item, items
        )
        dropdown.adapter = adapter
        dropdown.onItemSelectedListener = this
    }

    private fun runTextRecognition() {
        val image = mSelectedImage?.let { InputImage.fromBitmap(it, 0) } ?: return
        val recognizer = TextRecognition.getClient()
        mTextButton?.isEnabled = false
        recognizer.process(image)
            .addOnSuccessListener { texts ->
                mTextButton?.isEnabled = true
                processTextRecognitionResult(texts)
            }
            .addOnFailureListener { e -> // Task failed with an exception
                mTextButton?.isEnabled = true
                e.printStackTrace()
            }
    }

    private fun processTextRecognitionResult(texts: Text) {
        val blocks = texts.textBlocks
        if (blocks.size == 0) {
            showToast("No text found")
            return
        }
        mGraphicOverlay?.clear()
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    val textGraphic = mGraphicOverlay?.let { TextGraphic(it, elements[k]) }
                    textGraphic?.let { mGraphicOverlay?.add(it) }
                }
            }
        }
    }

    private fun runFaceContourDetection() {
        val image = mSelectedImage?.let { InputImage.fromBitmap(it, 0) } ?: return
        val options =
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()

        mFaceButton?.isEnabled = false
        val detector = FaceDetection.getClient(options)
        detector.process(image)
            .addOnSuccessListener { faces ->
                mFaceButton?.isEnabled = true
                processFaceContourDetectionResult(faces)
            }
            .addOnFailureListener { e -> // Task failed with an exception
                mFaceButton?.isEnabled = true
                e.printStackTrace()
            }
    }

    private fun processFaceContourDetectionResult(faces: List<Face>) {
        // Task completed successfully
        if (faces.isEmpty()) {
            showToast("No face found")
            return
        }
        mGraphicOverlay?.clear()
        for (i in faces.indices) {
            val face = faces[i]
            if (mGraphicOverlay == null) return
            val faceGraphic = FaceContourGraphic(mGraphicOverlay!!)
            mGraphicOverlay?.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }


    // Functions for loading images from app assets.
    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private fun getImageMaxWidth(): Int {
        if (mImageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxWidth = mImageView?.width
        }

        return mImageMaxWidth ?: 0
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private fun getImageMaxHeight(): Int {
        if (mImageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            mImageMaxHeight =
                mImageView?.height
        }

        return mImageMaxHeight ?: 0
    }

    // Gets the targeted width / height.
    private fun getTargetedWidthHeight(): Pair<Int, Int> {
        val targetWidth: Int
        val targetHeight: Int
        val maxWidthForPortraitMode = getImageMaxWidth()
        val maxHeightForPortraitMode = getImageMaxHeight()
        targetWidth = maxWidthForPortraitMode
        targetHeight = maxHeightForPortraitMode
        return Pair(targetWidth, targetHeight)
    }

    override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
        mGraphicOverlay?.clear()
        when (position) {
            0 -> mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg")
            1 ->                 // Whatever you want to happen when the thrid item gets selected
                mSelectedImage = getBitmapFromAsset(this, "grace_hopper.jpg")
        }
        if (mSelectedImage != null) {
            // Get the dimensions of the View
            val targetedSize = getTargetedWidthHeight()

            val targetWidth = targetedSize.first
            val maxHeight = targetedSize.second

            // Determine how much to scale down the image
            val scaleFactor = max(
                ((mSelectedImage?.width?.toFloat() ?: 0f) / targetWidth.toFloat()).toDouble(),
                ((mSelectedImage?.height?.toFloat() ?: 0f )/ maxHeight.toFloat()).toDouble()
            ).toFloat()

            if (mSelectedImage != null) {
                val resizedBitmap =
                    Bitmap.createScaledBitmap(
                        mSelectedImage!!,
                        ((mSelectedImage?.width ?: 0) / scaleFactor).toInt(),
                        ((mSelectedImage?.height ?: 0) / scaleFactor).toInt(),
                        true
                    )

                mImageView?.setImageBitmap(resizedBitmap)
                mSelectedImage = resizedBitmap
            }
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing
    }

    private fun getBitmapFromAsset(context: Context, filePath: String?): Bitmap? {
        val assetManager = context.assets

        var bitmap: Bitmap? = null
        try {
            val input = assetManager.open(filePath!!)
            bitmap = BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return bitmap
    }
}