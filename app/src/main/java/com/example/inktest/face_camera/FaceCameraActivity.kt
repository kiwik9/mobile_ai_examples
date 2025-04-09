package com.example.inktest.face_camera

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.inktest.R
import com.example.inktest.face.FaceContourGraphic
import com.example.inktest.face.GraphicOverlay
import com.example.inktest.face.TextGraphic
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceCameraActivity : AppCompatActivity() {
    // Vista de la cámara y capa de dibujo para superponer gráficos (texto o rostro)
    private lateinit var previewView: PreviewView
    private lateinit var graphicOverlay: GraphicOverlay

    // Botones para cambiar entre los modos de detección (texto o rostro)
    private lateinit var textButton: Button
    private lateinit var faceButton: Button

    // Modo de detección actual (ninguno, texto o rostro)
    private var detectionMode: DetectionMode = DetectionMode.NONE

    // Executor para manejar análisis de imagen en un hilo separado
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var imageAnalysis: ImageAnalysis

    // Modos de detección posibles
    enum class DetectionMode {
        NONE, TEXT, FACE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_camera)

        // Inicializa las vistas de cámara y botones
        previewView = findViewById(R.id.preview_view)
        graphicOverlay = findViewById(R.id.graphic_overlay)
        textButton = findViewById(R.id.button_text)
        faceButton = findViewById(R.id.button_face)

        // Listener para activar el modo de reconocimiento de texto
        textButton.setOnClickListener {
            detectionMode = DetectionMode.TEXT
            showToast("Modo texto activado")
        }

        // Listener para activar el modo de detección de rostros
        faceButton.setOnClickListener {
            detectionMode = DetectionMode.FACE
            showToast("Modo rostro activado")
        }

        // Executor para manejar análisis de imágenes de manera asíncrona
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Verifica si los permisos están concedidos antes de iniciar la cámara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                10
            )
        }
    }

    // Verifica si el permiso de cámara está concedido
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    // Inicializa la cámara y configura análisis de imagen y preview
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Configura la vista previa de la cámara
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Configura el análisis de imagen para resolución 1280x720 y análisis en tiempo real
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // Se asigna el analizador que se ejecutará por cada frame capturado
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Selecciona la cámara trasera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Desconecta cualquier uso anterior de la cámara y conecta la nueva configuración
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                showToast("No se pudo iniciar la cámara")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Procesa cada imagen capturada por la cámara usando ML Kit
    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        when (detectionMode) {
            DetectionMode.TEXT -> {
                // Inicializa el detector de texto de ML Kit
                val recognizer = TextRecognition.getClient()
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        // Limpia los gráficos previos y dibuja cada elemento de texto detectado
                        graphicOverlay.clear()
                        visionText.textBlocks.forEach { block ->
                            block.lines.forEach { line ->
                                line.elements.forEach { element ->
                                    graphicOverlay.add(TextGraphic(graphicOverlay, element))
                                }
                            }
                        }
                    }
                    .addOnFailureListener { it.printStackTrace() }
                    .addOnCompleteListener { imageProxy.close() }
            }

            DetectionMode.FACE -> {
                // Configura las opciones del detector de rostro con contornos
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .build()

                // Inicializa el detector de rostros de ML Kit
                val detector = FaceDetection.getClient(options)
                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        // Limpia los gráficos previos y dibuja los contornos de los rostros detectados
                        graphicOverlay.clear()
                        for (face in faces) {
                            val faceGraphic = FaceContourGraphic(graphicOverlay)
                            graphicOverlay.add(faceGraphic)
                            faceGraphic.updateFace(face)
                        }
                    }
                    .addOnFailureListener { it.printStackTrace() }
                    .addOnCompleteListener { imageProxy.close() }
            }

            else -> {
                // Cierra el frame si no se está usando ningún modo de detección
                imageProxy.close()
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
