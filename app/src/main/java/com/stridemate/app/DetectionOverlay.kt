package com.stridemate.app

import android.content.Context
import android.graphics.*
import android.view.View

class DetectionOverlay(context: Context) : View(context) {

    // ✅ GREEN BOXES for object detection
    private val paintObjectBox = Paint().apply {
        color = Color.rgb(0, 255, 0)  // Bright green
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    // ✅ Semi-transparent green background for object labels
    private val paintLabelBg = Paint().apply {
        color = Color.argb(220, 0, 200, 0)
        style = Paint.Style.FILL
    }

    private val paintObjectText = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    // ✅ BLUE BOXES for text recognition (OCR)
    private val paintTextBox = Paint().apply {
        color = Color.rgb(0, 150, 255)  // Blue
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val paintTextBg = Paint().apply {
        color = Color.argb(220, 0, 100, 255)
        style = Paint.Style.FILL
    }

    private val paintOcrText = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private var objectDetections: List<DetectionResult> = emptyList()
    private var textBlocks: List<MLKitTextRecognizer.TextBlock> = emptyList()

    fun setObjectDetections(results: List<DetectionResult>) {
        objectDetections = results
        postInvalidate()
    }

    fun setTextDetections(texts: List<MLKitTextRecognizer.TextBlock>) {
        textBlocks = texts
        postInvalidate()
    }

    fun clear() {
        objectDetections = emptyList()
        textBlocks = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ✅ Draw object detections (YOLOv8) with GREEN boxes and DISTANCE
        for (det in objectDetections) {
            // Draw green bounding box
            canvas.drawRect(det.boundingBox, paintObjectBox)

            // Prepare label text with distance
            val confText = det.confidence?.let { " ${(it * 100).toInt()}%" } ?: ""
            val distanceText = det.distance?.let {
                " • ${String.format("%.1f", it)}m"
            } ?: ""
            val labelText = "${det.label}$confText$distanceText"

            // Measure text size
            val textBounds = Rect()
            paintObjectText.getTextBounds(labelText, 0, labelText.length, textBounds)

            val labelX = det.boundingBox.left
            val labelY = det.boundingBox.top - 15

            // Draw background rectangle for label
            canvas.drawRect(
                labelX,
                labelY - textBounds.height() - 15,
                labelX + textBounds.width() + 25,
                labelY + 5,
                paintLabelBg
            )

            // Draw label text
            canvas.drawText(labelText, labelX + 12, labelY - 5, paintObjectText)
        }

        // ✅ Draw text detections (OCR) with BLUE boxes
        for (text in textBlocks) {
            // Draw blue bounding box
            canvas.drawRect(text.boundingBox, paintTextBox)

            // Prepare OCR text
            val displayText = if (text.text.length > 20) {
                text.text.substring(0, 20) + "..."
            } else {
                text.text
            }

            // Measure text size
            val textBounds = Rect()
            paintOcrText.getTextBounds(displayText, 0, displayText.length, textBounds)

            val textX = text.boundingBox.left
            val textY = text.boundingBox.bottom + textBounds.height() + 20

            // Draw background rectangle for text
            canvas.drawRect(
                textX,
                textY - textBounds.height() - 15,
                textX + textBounds.width() + 25,
                textY + 5,
                paintTextBg
            )

            // Draw recognized text
            canvas.drawText(displayText, textX + 12, textY - 5, paintOcrText)
        }
    }
}