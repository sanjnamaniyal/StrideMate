package com.stridemate.app

import android.graphics.RectF

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float? = null,
    val distance: Float? = null  // ✅ Added distance in meters
)