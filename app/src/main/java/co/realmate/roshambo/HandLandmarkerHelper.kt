package co.realmate.roshambo

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/** Wraps MediaPipe's HandLandmarker in live-stream mode. */
class HandLandmarkerHelper(
    context: Context,
    private val onResult: (HandLandmarkerResult) -> Unit
) {
    private var landmarker: HandLandmarker? = null

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { /* ignore transient frame errors */ }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    /** The image is expected to already be rotated upright by the caller. */
    fun detect(image: MPImage, timestampMs: Long) {
        landmarker?.detectAsync(image, timestampMs)
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
