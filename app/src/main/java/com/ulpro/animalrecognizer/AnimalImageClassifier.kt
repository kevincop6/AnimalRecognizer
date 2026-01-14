package com.ulpro.animalrecognizer.ml

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// --------------------------------------------------
// RESULTADO PÚBLICO
// --------------------------------------------------
public data class ClassificationResult(
    public val className: String,
    public val confidence: Float
)

// --------------------------------------------------
// CLASIFICADOR PÚBLICO Y REUTILIZABLE
// --------------------------------------------------
public class AnimalImageClassifier(
    private val context: Context
) {

    private val inputSize: Int = 224
    private val modelName: String = "model.tflite"
    private val classesName: String = "classes.json"

    private val interpreter: Interpreter by lazy { loadModel() }
    private val classMapping: Map<String, String> by lazy { loadClassMapping() }

    // ==================================================
    // API PÚBLICA
    // ==================================================
    public fun classify(bitmap: Bitmap): ClassificationResult {
        val safeBitmap = ensureSoftwareBitmap(bitmap)
        val inputBuffer = preprocessImage(safeBitmap)

        val output = Array(1) { FloatArray(classMapping.size) }
        interpreter.run(inputBuffer, output)

        val predictions = output[0]
        val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0

        val name = classMapping[maxIndex.toString()] ?: "Desconocido"
        val confidence = predictions[maxIndex]

        return ClassificationResult(
            className = name,
            confidence = confidence
        )
    }

    public fun close() {
        interpreter.close()
    }

    // ==================================================
    // INTERNOS (PRIVATE)
    // ==================================================
    private fun loadModel(): Interpreter {
        val afd = context.assets.openFd(modelName)
        val input = FileInputStream(afd.fileDescriptor)
        val buffer = input.channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
        return Interpreter(buffer)
    }

    private fun loadClassMapping(): Map<String, String> {
        val json = context.assets.open(classesName)
            .bufferedReader()
            .use { it.readText() }

        val obj = JSONObject(json).getJSONObject("class_mapping")
        return obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer
            .allocateDirect(4 * inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (px in pixels) {
            buffer.putFloat(((px shr 16) and 0xFF) / 255f * 2f - 1f)
            buffer.putFloat(((px shr 8) and 0xFF) / 255f * 2f - 1f)
            buffer.putFloat((px and 0xFF) / 255f * 2f - 1f)
        }

        buffer.rewind()
        return buffer
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else bitmap
    }
}
