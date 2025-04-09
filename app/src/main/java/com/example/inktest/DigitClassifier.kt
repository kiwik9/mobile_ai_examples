/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.inktest

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.Interpreter

class DigitClassifier(private val context: Context) {
  // Intérprete de TensorFlow Lite (se inicializa luego)
  private var interpreter: Interpreter? = null

  var isInitialized = false
    private set

  // Servicio para ejecutar tareas en segundo plano
  private val executorService: ExecutorService = Executors.newCachedThreadPool()

  // Dimensiones esperadas de entrada del modelo
  private var inputImageWidth: Int = 0
  private var inputImageHeight: Int = 0
  private var modelInputSize: Int = 0

  // Inicializa el intérprete de forma asíncrona y retorna una tarea
  fun initialize(): Task<Void?> {
    val task = TaskCompletionSource<Void?>()
    executorService.execute {
      try {
        initializeInterpreter() // Carga y configura el modelo
        task.setResult(null)
      } catch (e: IOException) {
        task.setException(e)
      }
    }
    return task.task
  }

  // Carga el archivo .tflite y configura el intérprete
  // Obtener el modelo: https://colab.research.google.com/github/tensorflow/examples/blob/master/lite/codelabs/digit_classifier/ml/step2_train_ml_model.ipynb
  @Throws(IOException::class)
  private fun initializeInterpreter() {
    val assetManager = context.assets
    val model = loadModelFile(assetManager, "mnist.tflite")
    val interpreter = Interpreter(model)

    // Obtiene las dimensiones de entrada del modelo
    val inputShape = interpreter.getInputTensor(0).shape()
    inputImageWidth = inputShape[1]
    inputImageHeight = inputShape[2]
    modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * PIXEL_SIZE

    this.interpreter = interpreter
    isInitialized = true
    Log.d(TAG, "Initialized TFLite interpreter.")
  }

  // Carga el modelo desde los assets en un ByteBuffer
  @Throws(IOException::class)
  private fun loadModelFile(assetManager: AssetManager, filename: String): ByteBuffer {
    val fileDescriptor = assetManager.openFd(filename)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    val startOffset = fileDescriptor.startOffset
    val declaredLength = fileDescriptor.declaredLength
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
  }

  // Clasifica una imagen de forma sincrónica
  private fun classify(bitmap: Bitmap): String {
    check(isInitialized) { "TF Lite Interpreter is not initialized yet." }

    // Redimensiona la imagen al tamaño esperado por el modelo
    val resizedImage = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
    val byteBuffer = convertBitmapToByteBuffer(resizedImage)

    // Prepara la salida (probabilidades para cada clase)
    val output = Array(1) { FloatArray(OUTPUT_CLASSES_COUNT) }

    // Ejecuta el modelo
    interpreter?.run(byteBuffer, output)

    // Obtiene el índice con la probabilidad más alta
    val result = output[0]
    val maxIndex = result.indices.maxByOrNull { result[it] } ?: -1
    val resultString = "Prediction Result: %d\nConfidence: %2f".format(maxIndex, result[maxIndex])

    return resultString
  }

  // Clasifica una imagen de forma asíncrona y retorna una Task
  fun classifyAsync(bitmap: Bitmap): Task<String> {
    val task = TaskCompletionSource<String>()
    executorService.execute {
      val result = classify(bitmap)
      task.setResult(result)
    }
    return task.task
  }

  // Libera recursos del intérprete
  fun close() {
    executorService.execute {
      interpreter?.close()
      Log.d(TAG, "Closed TFLite interpreter.")
    }
  }

  // Convierte una imagen Bitmap a un ByteBuffer que el modelo pueda procesar
  private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
    val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
    byteBuffer.order(ByteOrder.nativeOrder())

    // Extrae los pixeles de la imagen
    val pixels = IntArray(inputImageWidth * inputImageHeight)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    // Convierte a escala de grises y normaliza a valores entre 0 y 1
    for (pixelValue in pixels) {
      val r = (pixelValue shr 16 and 0xFF)
      val g = (pixelValue shr 8 and 0xFF)
      val b = (pixelValue and 0xFF)
      val normalizedPixelValue = (r + g + b) / 3.0f / 255.0f
      byteBuffer.putFloat(normalizedPixelValue)
    }

    return byteBuffer
  }

  // Constantes de configuración
  companion object {
    private const val TAG = "DigitClassifier"
    private const val FLOAT_TYPE_SIZE = 4
    private const val PIXEL_SIZE = 1
    private const val OUTPUT_CLASSES_COUNT = 10
  }
}
