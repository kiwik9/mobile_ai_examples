package com.example.inktest

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.inktest.digit.DigitActivity
import com.example.inktest.face.FaceActivity
import com.example.inktest.face_camera.FaceCameraActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setButtonsLogic()
    }

    private fun setButtonsLogic() {
        val digitButton = findViewById<Button>(R.id.btnToDigit)
        val faceButton = findViewById<Button>(R.id.btnToFaceRecognition)
        val faceCamera = findViewById<Button>(R.id.btnFaceRecognitionCamera)

        digitButton.setOnClickListener {
            startActivity(Intent(this, DigitActivity::class.java))
        }

        faceButton.setOnClickListener {
            startActivity(Intent(this, FaceActivity::class.java))
        }

        faceCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivity(Intent(this, FaceCameraActivity::class.java))
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.CAMERA),
                    0
                )
            }
        }
    }

}