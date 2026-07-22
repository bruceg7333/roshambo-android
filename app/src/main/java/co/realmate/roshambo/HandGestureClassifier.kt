package co.realmate.roshambo

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Classifies rock/paper/scissors from MediaPipe's 21 hand landmarks.
 *
 * A finger counts as extended only when it is BOTH fairly straight (large angle
 * at the PIP joint) AND pointing away from the palm (tip farther from the wrist
 * than the knuckle). The angle test is robust to hand orientation; the distance
 * test reliably rejects folded fingers (e.g. ring/little in a scissors gesture).
 *
 * MediaPipe landmark indices: wrist 0; index 5/6/7/8; middle 9/10/11/12;
 * ring 13/14/15/16; little 17/18/19/20 (MCP/PIP/DIP/TIP).
 */
object HandGestureClassifier {

    private const val EXTENDED_ANGLE = 1.95   // radians (~112°)

    fun classify(landmarks: List<NormalizedLandmark>): Gesture {
        if (landmarks.size < 21) return Gesture.NONE
        val wrist = landmarks[0]

        fun extended(mcp: Int, pip: Int, tip: Int): Boolean {
            val straight = angle(landmarks[mcp], landmarks[pip], landmarks[tip]) > EXTENDED_ANGLE
            val pointingOut = dist(landmarks[tip], wrist) > dist(landmarks[mcp], wrist) * 1.08
            return straight && pointingOut
        }

        val index = extended(5, 6, 8)
        val middle = extended(9, 10, 12)
        val ring = extended(13, 14, 16)
        val little = extended(17, 18, 20)
        val count = listOf(index, middle, ring, little).count { it }

        return when {
            index && middle && !ring && !little -> Gesture.SCISSORS
            count >= 3 -> Gesture.PAPER
            count <= 1 -> Gesture.ROCK
            else -> Gesture.NONE
        }
    }

    private fun angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val v1x = a.x() - b.x(); val v1y = a.y() - b.y()
        val v2x = c.x() - b.x(); val v2y = c.y() - b.y()
        val m1 = hypot(v1x.toDouble(), v1y.toDouble())
        val m2 = hypot(v2x.toDouble(), v2y.toDouble())
        if (m1 == 0.0 || m2 == 0.0) return Math.PI
        val cosine = (v1x * v2x + v1y * v2y) / (m1 * m2)
        return acos(cosine.coerceIn(-1.0, 1.0))
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Double =
        hypot((a.x() - b.x()).toDouble(), (a.y() - b.y()).toDouble())
}
