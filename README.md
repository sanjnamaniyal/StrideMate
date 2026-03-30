StrideMate – AI-Powered Navigation Assistant for the Visually Impaired
StrideMate is an intelligent mobile navigation assistant designed to support visually impaired users in understanding and interacting with their surroundings. The application combines real-time computer vision, object detection, text recognition, voice feedback, and navigation assistance into a single accessible Android application.
Using the device camera, StrideMate continuously detects nearby objects and provides spoken feedback such as the object name and estimated distance. It also includes OCR-based text recognition to read signs, labels, and printed text aloud, helping users navigate indoor and outdoor environments more independently.

 Key Features:
1. Real-time object detection using YOLOv8
2. Bounding box visualization for detected objects
3. OCR-based text recognition using ML Kit
4. Voice feedback through Text-to-Speech
5. Voice command support using Speech Recognition
6. Torch/flashlight toggle for low-light environments
7. Google Maps navigation integration
8. Estimated object distance using AI-based depth estimation
9. Accessible, voice-first user interface designed for ease of use

Technologies Used:
1. Kotlin
2. Android Studio
3. Jetpack Compose
4. CameraX
5. YOLOv8
6. ML Kit OCR
7. TensorFlow Lite
8. Text-to-Speech (TTS)
9. SpeechRecognizer API
10. Google Maps Intent Integration

 Working Principle:
1. The camera feed is captured in real time using CameraX.
2. YOLOv8 processes each frame to detect nearby objects.
3. Detected objects are highlighted with bounding boxes and their labels are spoken aloud.
4. ML Kit OCR extracts readable text from the environment and announces it to the user.
5. Depth estimation is used to approximate the distance of nearby objects.
6. Voice commands allow the user to open the camera, start navigation, enable OCR, or return to the previous screen.

Objective:
The aim of StrideMate is to improve independence, mobility, and safety for visually impaired individuals by providing an affordable, portable, and intelligent navigation solution directly on a smartphone.
