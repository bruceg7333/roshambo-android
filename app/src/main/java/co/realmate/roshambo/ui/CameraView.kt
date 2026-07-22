package co.realmate.roshambo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import co.realmate.roshambo.HandLandmarkerHelper
import co.realmate.roshambo.RoshamboViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val BONES = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    5 to 9, 9 to 13, 13 to 17
)

@Composable
fun CameraView(vm: RoshamboViewModel, useBack: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val helper = remember {
        HandLandmarkerHelper(context) { result -> mainHandler.post { vm.onResult(result) } }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastTs = remember { AtomicLong(0L) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose { helper.close(); analysisExecutor.shutdown() }
    }

    // Rebind whenever the selected lens changes.
    LaunchedEffect(useBack) {
        vm.setMirror(!useBack)
        val provider = providerFuture.awaitProvider(context)
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(selector).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val raw = proxy.toBitmap()
            val rot = proxy.imageInfo.rotationDegrees
            val upright = if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else raw
            val aspect = upright.width.toFloat() / upright.height
            mainHandler.post { vm.setAspect(aspect) }
            val ts = maxOf(SystemClock.uptimeMillis(), lastTs.get() + 1)
            lastTs.set(ts)
            helper.detect(BitmapImageBuilder(upright).build(), ts)
            proxy.close()
        }
        val lens = if (useBack) CameraSelector.DEFAULT_BACK_CAMERA
        else CameraSelector.DEFAULT_FRONT_CAMERA
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, lens, preview, analysis)
    }

    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        HandSkeleton(vm.landmarks, vm.imageAspect, Modifier.fillMaxSize())
    }
}

private suspend fun ListenableFuture<ProcessCameraProvider>.awaitProvider(
    context: Context
): ProcessCameraProvider = suspendCoroutine { cont ->
    addListener({ cont.resume(get()) }, ContextCompat.getMainExecutor(context))
}

@Composable
private fun HandSkeleton(points: List<Offset>, imageAspect: Float, modifier: Modifier) {
    if (points.size < 21) return
    Canvas(modifier) {
        val viewAspect = size.width / size.height
        val dispW: Float
        val dispH: Float
        if (imageAspect > viewAspect) {
            dispH = size.height; dispW = dispH * imageAspect
        } else {
            dispW = size.width; dispH = dispW / imageAspect
        }
        val offX = (size.width - dispW) / 2f
        val offY = (size.height - dispH) / 2f
        fun map(p: Offset) = Offset(offX + p.x * dispW, offY + p.y * dispH)

        for ((a, b) in BONES) {
            val pa = map(points[a]); val pb = map(points[b])
            drawLine(Palette.accent2.copy(alpha = 0.35f), pa, pb, strokeWidth = 12f, cap = StrokeCap.Round)
            drawLine(Palette.accent2, pa, pb, strokeWidth = 4f, cap = StrokeCap.Round)
        }
        for (p in points) {
            val c = map(p)
            drawCircle(Palette.accent2.copy(alpha = 0.4f), radius = 8f, center = c)
            drawCircle(androidx.compose.ui.graphics.Color.White, radius = 5f, center = c)
        }
    }
}
