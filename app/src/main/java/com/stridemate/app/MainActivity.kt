package com.stridemate.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.*
import kotlinx.coroutines.*
import java.util.*

class MainActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var hasAudioPermission by mutableStateOf(false)
    private var hasLocationPermission by mutableStateOf(false)
    private lateinit var tts: TextToSpeech
    private var isTtsInitialized = false
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Text-to-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.ENGLISH
                tts.setSpeechRate(0.95f)
                isTtsInitialized = true
            }
        }

        requestPermissions()

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "splash") {

                    composable("splash") {
                        SplashScreen {
                            navController.navigate("home") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    }

                    composable("home") {
                        HomeScreen(
                            onOpenCamera = {
                                vibrate()
                                speakMessage("Camera activated for object and text detection")
                                navController.navigate("camera")
                            },
                            onVoiceCommand = { command ->
                                handleVoiceCommand(command, navController)
                            },
                            hasAudioPermission = hasAudioPermission,
                            onHelpClick = { navController.navigate("help") },
                            onSettingsClick = { navController.navigate("settings") },

                            // Navigation button clicked
                            onNavigationClick = {
                                vibrate()
                                speakMessage("Opening Google Maps")
                                openGoogleMapsNavigation("Bangalore")
                            }
                        )
                    }

                    composable("help") { HelpScreen(onBack = { navController.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }

                    composable("camera") {
                        CameraScreen(
                            onBack = {
                                vibrate()
                                speakMessage("Returning to home screen")
                                navController.popBackStack()
                            },
                            hasCameraPermission = hasCameraPermission,
                            onToggleTorch = { toggleTorch() },
                            onCameraReady = { cam -> camera = cam }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (isTtsInitialized) speakMessage("Welcome to StrideMate. Your smart navigation assistant")
    }

    private fun requestPermissions() {
        val cameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasCameraPermission = it
        }
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) cameraLauncher.launch(Manifest.permission.CAMERA)

        val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasAudioPermission = it
        }
        hasAudioPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)

        val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasLocationPermission = it
        }
        hasLocationPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // GOOGLE MAPS NAVIGATION FUNCTION
    fun openGoogleMapsNavigation(destination: String) {
        try {
            val uri = android.net.Uri.parse("google.navigation:q=${destination.replace(" ", "+")}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        } catch (e: Exception) {
            speakMessage("Google Maps not installed")
        }
    }

    private fun handleVoiceCommand(command: String, navController: NavController) {
        val text = command.lowercase(Locale.getDefault())

        //  Voice: "start navigation", "open navigation", "navigation"
        if ("start navigation" in text ||
            "open navigation" in text ||
            "navigation" in text
        ) {
            vibrate()
            speakMessage("Opening Google Maps")
            openGoogleMapsNavigation("Bangalore") // Change default if you want
            return
        }

        //  Voice: "navigate to MG Road"
        if (text.startsWith("navigate to")) {
            val destination = text.substringAfter("navigate to").trim()
            if (destination.isNotEmpty()) {
                vibrate()
                speakMessage("Navigating to $destination")
                openGoogleMapsNavigation(destination)
                return
            }
        }

        when {
            "open camera" in text || "start camera" in text -> {
                vibrate()
                speakMessage("Camera activated")
                navController.navigate("camera")
            }

            "back" in text || "go back" in text -> {
                vibrate()
                speakMessage("Going back")
                navController.popBackStack()
            }

            "exit" in text || "close" in text -> {
                vibrate()
                speakMessage("Closing StrideMate")
                finish()
            }

            "help" in text -> speakMessage("Available commands: open camera, start navigation, navigate to place, back, exit")

            else -> speakMessage("Command not recognized")
        }
    }

    private fun toggleTorch() {
        camera?.let {
            val torchEnabled = it.cameraInfo.torchState.value == TorchState.ON
            it.cameraControl.enableTorch(!torchEnabled)

            speakMessage(if (torchEnabled) "Torch off" else "Torch on")
        }
    }


    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        else vibrator.vibrate(200)
    }

    fun speakMessage(message: String) {
        if (::tts.isInitialized && isTtsInitialized) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    hasCameraPermission: Boolean,
    onToggleTorch: () -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    var ocrEnabled by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewScreen(onCameraReady, ocrEnabled)
        } else {
            PermissionDeniedScreen()
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
            FloatingActionButton(onClick = onToggleTorch) {
                Icon(Icons.Filled.FlashOn, "Torch")
            }
            FloatingActionButton(
                onClick = { ocrEnabled = !ocrEnabled },
                containerColor = if (ocrEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Filled.TextFields, "OCR")
            }
        }

        if (ocrEnabled) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    "📖 Text Recognition Active",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreviewScreen(onCameraReady: (Camera) -> Unit, ocrEnabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val yoloDetector = remember { YOLOv8Detector(context) }
    val textRecognizer = remember { MLKitTextRecognizer() }
    val depthEstimator = remember { MiDaSDepthEstimator(context) }

    DisposableEffect(Unit) {
        onDispose {
            yoloDetector.close()
            textRecognizer.close()
            depthEstimator.close()
        }
    }

    var lastSpokenObject by remember { mutableStateOf("") }
    var lastObjectTime by remember { mutableStateOf(0L) }
    var lastSpokenText by remember { mutableStateOf("") }
    var lastTextTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val overlay = DetectionOverlay(ctx)
            val layout = android.widget.FrameLayout(ctx).apply {
                addView(previewView)
                addView(overlay)
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->

                    if (isProcessing) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val bitmap = previewView.bitmap
                    if (bitmap != null) {
                        isProcessing = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val objects = yoloDetector.detect(bitmap)
                                val depthMap = depthEstimator.estimateDepth(bitmap)

                                val objectsWithDistance =
                                    depthMap?.let { map ->
                                        objects.map { obj ->
                                            val relDepth = depthEstimator.getDepthForBox(
                                                map, obj.boundingBox, bitmap.width, bitmap.height
                                            )
                                            val meters = relDepth?.let {
                                                depthEstimator.depthToMeters(it)
                                            }
                                            obj.copy(distance = meters)
                                        }
                                    } ?: objects

                                val texts =
                                    if (ocrEnabled) textRecognizer.recognizeText(bitmap) else emptyList()

                                withContext(Dispatchers.Main) {
                                    overlay.setObjectDetections(objectsWithDistance)
                                    overlay.setTextDetections(texts)

                                    val now = System.currentTimeMillis()

                                    // SPEAK OBJECT
                                    objectsWithDistance.firstOrNull()?.let { obj ->
                                        if (obj.label != lastSpokenObject || now - lastObjectTime > 5000) {
                                            val d = obj.distance?.let {
                                                " at ${String.format("%.1f", it)} meters"
                                            } ?: ""
                                            (context as MainActivity).speakMessage("${obj.label}$d")
                                            lastSpokenObject = obj.label
                                            lastObjectTime = now
                                        }
                                    }

                                    // SPEAK OCR
                                    texts.firstOrNull()?.let { text ->
                                        if (ocrEnabled &&
                                            text.confidence >= 0.7f &&
                                            (text.text != lastSpokenText || now - lastTextTime > 5000)
                                        ) {
                                            (context as MainActivity).speakMessage("Text says: ${text.text}")
                                            lastSpokenText = text.text
                                            lastTextTime = now
                                        }
                                    }
                                }
                            } finally {
                                isProcessing = false
                            }
                        }
                    }

                    imageProxy.close()
                }

                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )

                onCameraReady(camera)

            }, ContextCompat.getMainExecutor(ctx))

            layout
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PermissionDeniedScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Error, null, Modifier.size(60.dp))
            Spacer(Modifier.height(16.dp))
            Text("Permission Required", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) { delay(2000); onTimeout() }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Accessible, null, Modifier.size(100.dp))
            Spacer(Modifier.height(24.dp))
            Text("StrideMate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Your smart navigation assistant")
        }
    }
}

@Composable
fun HomeScreen(
    onOpenCamera: () -> Unit,
    onVoiceCommand: (String) -> Unit,
    hasAudioPermission: Boolean,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigationClick: () -> Unit
) {
    val context = LocalContext.current
    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context)
        else null
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (_: Exception) {
            isListening = false
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            delay(500)
            startListening()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                coroutineScope.launch { delay(800); startListening() }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                recognizedText = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                if (recognizedText.isNotEmpty()) onVoiceCommand(recognizedText)
                coroutineScope.launch { delay(500); startListening() }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose { speechRecognizer?.destroy() }
    }

    val micScale by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "mic"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Filled.Accessible, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Text("StrideMate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            Text("Your smart navigation assistant", color = MaterialTheme.colorScheme.onPrimary)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onOpenCamera,
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Camera, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Camera")
            }

            OutlinedButton(
                onClick = onNavigationClick,
                modifier = Modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.Navigation, null)
                Spacer(Modifier.width(8.dp))
                Text("Navigation")
            }

            if (isListening) {
                Spacer(Modifier.height(16.dp))
                Icon(Icons.Filled.Mic, null, Modifier.size(50.dp).scale(micScale), tint = MaterialTheme.colorScheme.onPrimary)
                Text("Listening…", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHelpClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("Help")
                }
                OutlinedButton(onClick = onSettingsClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text("Settings")
                }
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Help & Commands", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Divider()

            Text("Voice Commands:", fontWeight = FontWeight.Bold)
            Text("• 'Open Camera' - Start detection")
            Text("• 'Back' - Go to previous screen")
            Text("• 'Exit' - Close the app")

            Spacer(Modifier.height(8.dp))

            Text("Camera Features:", fontWeight = FontWeight.Bold)
            Text("• Green boxes = Detected objects with distance")
            Text("• Blue boxes = Recognized text")
            Text("• Tap OCR button to toggle text recognition")
            Text("• Tap flash icon for torch")

            Spacer(Modifier.height(8.dp))

            Text("Notes:", fontWeight = FontWeight.Bold)
            Text("• Object detection runs continuously")
            Text("• Distance estimation using AI depth model")
            Text("• Text recognition works offline")
            Text("• Voice alerts have cooldown")
        }

        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, "Back")
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Divider()

            Text("• Voice feedback: Enabled")
            Text("• Haptic feedback: Enabled")
            Text("• OCR language: English (Offline)")
            Text("• Detection confidence: 55%")
            Text("• Voice cooldown: 5 seconds")
            Text("• Depth estimation: MiDaS (max 5m)")
        }

        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, "Back")
        }
    }
}
