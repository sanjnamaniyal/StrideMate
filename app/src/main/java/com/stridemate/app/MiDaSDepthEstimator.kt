package com.stridemate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class MiDaSDepthEstimator(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val modelSize = 256 // MiDaS_small uses 256x256

    init {
        val modelBytes = context.assets.open("midas_small.onnx").readBytes()
        session = ortEnv.createSession(modelBytes)
        inputName = session.inputNames.iterator().next()
        android.util.Log.d("MiDaS", "✅ Depth model loaded: ${modelSize}x${modelSize}")
    }

    /**
     * Estimate depth map from bitmap
     * Returns normalized depth map (0.0 = far, 1.0 = near)
     */
    fun estimateDepth(bitmap: Bitmap): FloatArray? {
        return try {
            // Resize and normalize input
            val resized = Bitmap.createScaledBitmap(bitmap, modelSize, modelSize, true)
            val inputBuffer = preprocessImage(resized)

            // Create ONNX tensor
            val shape = longArrayOf(1, 3, modelSize.toLong(), modelSize.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputBuffer), shape)

            // Run inference
            val output = session.run(mapOf(inputName to inputTensor))
            val result = output[0].value as Array<*>

            // Extract depth values
            val depthData = (result[0] as Array<*>)[0] as FloatArray

            // Normalize to 0-1 range
            val minDepth = depthData.minOrNull() ?: 0f
            val maxDepth = depthData.maxOrNull() ?: 1f
            val normalized = depthData.map {
                (it - minDepth) / (maxDepth - minDepth)
            }.toFloatArray()

            inputTensor.close()
            output.close()

            normalized
        } catch (e: Exception) {
            android.util.Log.e("MiDaS", "❌ Depth estimation error: ${e.message}")
            null
        }
    }

    /**
     * Get average depth for a specific bounding box region
     * Returns relative depth (0.0 = far, 1.0 = near)
     */
    fun getDepthForBox(depthMap: FloatArray, bbox: RectF, imageWidth: Int, imageHeight: Int): Float? {
        if (depthMap.isEmpty()) return null

        // Scale bbox coordinates to depth map size
        val scaleX = modelSize.toFloat() / imageWidth
        val scaleY = modelSize.toFloat() / imageHeight

        val x1 = (bbox.left * scaleX).toInt().coerceIn(0, modelSize - 1)
        val y1 = (bbox.top * scaleY).toInt().coerceIn(0, modelSize - 1)
        val x2 = (bbox.right * scaleX).toInt().coerceIn(0, modelSize - 1)
        val y2 = (bbox.bottom * scaleY).toInt().coerceIn(0, modelSize - 1)

        var sum = 0f
        var count = 0

        for (y in y1..y2) {
            for (x in x1..x2) {
                val idx = y * modelSize + x
                if (idx < depthMap.size) {
                    sum += depthMap[idx]
                    count++
                }
            }
        }

        return if (count > 0) sum / count else null
    }

    /**
     * Convert relative depth (0-1) to approximate distance in meters
     * Higher depth value = closer object
     */
    fun depthToMeters(relativeDepth: Float, maxDistance: Float = 5.0f): Float {
        return ((1f - relativeDepth) * maxDistance).coerceIn(0.1f, maxDistance)
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(modelSize * modelSize)
        bitmap.getPixels(pixels, 0, modelSize, 0, 0, modelSize, modelSize)

        // ImageNet normalization for MiDaS
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val buffer = FloatArray(3 * modelSize * modelSize)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16 and 0xFF) / 255f - mean[0]) / std[0]
            val g = ((pixel shr 8 and 0xFF) / 255f - mean[1]) / std[1]
            val b = ((pixel and 0xFF) / 255f - mean[2]) / std[2]

            // CHW format (Channel, Height, Width)
            buffer[i] = r
            buffer[modelSize * modelSize + i] = g
            buffer[2 * modelSize * modelSize + i] = b
        }

        return buffer
    }

    fun close() {
        session.close()
        ortEnv.close()
    }
}