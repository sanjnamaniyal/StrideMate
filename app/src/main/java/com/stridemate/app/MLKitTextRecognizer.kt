package com.stridemate.app

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MLKitTextRecognizer {

    // ✅ On-device Latin script recognizer (works offline!)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class TextBlock(
        val text: String,
        val boundingBox: RectF,
        val confidence: Float
    )

    suspend fun recognizeText(bitmap: Bitmap): List<TextBlock> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            val textBlocks = mutableListOf<TextBlock>()

            for (block in result.textBlocks) {
                val text = block.text.trim()
                if (text.isEmpty() || text.length < 2) continue

                val boundingBox = block.boundingBox?.let {
                    RectF(it.left.toFloat(), it.top.toFloat(),
                        it.right.toFloat(), it.bottom.toFloat())
                } ?: continue

                // Estimate confidence based on text characteristics
                val confidence = when {
                    text.length >= 5 && text.any { it.isLetter() } -> 0.9f
                    text.length >= 3 -> 0.75f
                    else -> 0.6f
                }

                textBlocks.add(TextBlock(text, boundingBox, confidence))
            }

            android.util.Log.d("MLKitOCR", "✅ Found ${textBlocks.size} text blocks")
            textBlocks

        } catch (e: Exception) {
            android.util.Log.e("MLKitOCR", "❌ Error: ${e.message}")
            emptyList()
        }
    }

    fun close() {
        recognizer.close()
    }
}