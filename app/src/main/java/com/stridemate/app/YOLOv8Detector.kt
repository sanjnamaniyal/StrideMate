package com.stridemate.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.max
import kotlin.math.min

class YOLOv8Detector(context: Context) {

    private val labels: List<String> = FileUtil.loadLabels(context, "labels.txt")
    private val interpreter: Interpreter
    private val inputShape: IntArray
    private val modelH: Int
    private val modelW: Int
    private val confidenceThreshold = 0.55f  // ✅ Increased for better accuracy
    private val iouThreshold = 0.50f  // ✅ Increased to reduce duplicate boxes

    init {
        val model = FileUtil.loadMappedFile(context, "best_float16.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)

        inputShape = interpreter.getInputTensor(0).shape()
        modelH = inputShape[1]
        modelW = inputShape[2]

        android.util.Log.d("YOLOv8", "✅ Model loaded: ${modelW}x${modelH}, Classes: ${labels.size}")
        val outShape = interpreter.getOutputTensor(0).shape()
        android.util.Log.d("YOLOv8", "Output shape: ${outShape.contentToString()}")
    }

    private fun letterbox(src: Bitmap): Triple<Bitmap, Float, FloatArray> {
        val w = src.width
        val h = src.height
        val scale = min(modelW.toFloat() / w, modelH.toFloat() / h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
        val output = Bitmap.createBitmap(modelW, modelH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val dx = (modelW - newW) / 2f
        val dy = (modelH - newH) / 2f
        canvas.drawBitmap(resized, dx, dy, null)
        return Triple(output, scale, floatArrayOf(dx, dy, w.toFloat(), h.toFloat()))
    }

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {
        val (processed, scale, meta) = letterbox(bitmap)
        val dx = meta[0]
        val dy = meta[1]
        val origW = meta[2]
        val origH = meta[3]

        val input = Array(1) { Array(modelH) { Array(modelW) { FloatArray(3) } } }
        for (y in 0 until modelH) {
            for (x in 0 until modelW) {
                val px = processed.getPixel(x, y)
                input[0][y][x][0] = (px shr 16 and 0xFF) / 255f
                input[0][y][x][1] = (px shr 8 and 0xFF) / 255f
                input[0][y][x][2] = (px and 0xFF) / 255f
            }
        }

        val outTensor = interpreter.getOutputTensor(0)
        val os = outTensor.shape()
        val dimA = os[1]
        val dimB = os[2]

        var output = Array(1) { Array(dimA) { FloatArray(dimB) } }
        interpreter.run(input, output)

        var data = output[0]
        var numBoxes = dimA
        var values = dimB

        fun looksLikeValues(v: Int) = (v == 4 + labels.size || v == 5 + labels.size || v in (labels.size + 3)..(labels.size + 7))

        if (!looksLikeValues(values) && looksLikeValues(numBoxes)) {
            val transposed = Array(dimB) { FloatArray(dimA) }
            for (i in 0 until dimA)
                for (j in 0 until dimB)
                    transposed[j][i] = data[i][j]
            data = transposed
            numBoxes = dimB
            values = dimA
        }

        val hasObj = (values == 5 + labels.size)
        val rawDetections = mutableListOf<DetectionResult>()

        for (i in 0 until numBoxes) {
            val row = data[i]
            if (row.size < 4 + labels.size) continue

            val cx = row[0]
            val cy = row[1]
            val w = row[2]
            val h = row[3]

            val clsStart = if (hasObj) 5 else 4
            val classScores = row.copyOfRange(clsStart, row.size)
            val maxScore = classScores.maxOrNull() ?: continue
            val clsIndex = classScores.indices.maxByOrNull { classScores[it] } ?: continue

            if (maxScore < confidenceThreshold) continue

            val label = labels.getOrElse(clsIndex) { "Unknown" }
            val conf = if (hasObj) row[4] * maxScore else maxScore

            val x0 = ((cx - w / 2) - dx) / scale
            val y0 = ((cy - h / 2) - dy) / scale
            val x1 = ((cx + w / 2) - dx) / scale
            val y1 = ((cy + h / 2) - dy) / scale

            rawDetections.add(
                DetectionResult(
                    boundingBox = RectF(
                        max(0f, x0), max(0f, y0),
                        min(origW, x1), min(origH, y1)
                    ),
                    label = label,
                    confidence = conf
                )
            )
        }

        // ✅ Apply NMS and limit to top 5 detections
        return applyNMS(rawDetections).take(5)
    }

    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val sortedDetections = detections.sortedByDescending { it.confidence }
        val selectedDetections = mutableListOf<DetectionResult>()

        for (detection in sortedDetections) {
            var shouldSelect = true
            for (selected in selectedDetections) {
                // ✅ Only suppress if same class AND high overlap
                if (detection.label == selected.label &&
                    calculateIoU(detection.boundingBox, selected.boundingBox) > iouThreshold) {
                    shouldSelect = false
                    break
                }
            }
            if (shouldSelect) {
                selectedDetections.add(detection)
            }
        }

        return selectedDetections
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = max(box1.left, box2.left)
        val intersectionTop = max(box1.top, box2.top)
        val intersectionRight = min(box1.right, box2.right)
        val intersectionBottom = min(box1.bottom, box2.bottom)

        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0f
        }

        val intersectionArea = (intersectionRight - intersectionLeft) *
                (intersectionBottom - intersectionTop)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun close() {
        interpreter.close()
    }
}