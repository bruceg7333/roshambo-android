package co.realmate.roshambo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RoshamboViewModel : ViewModel() {

    enum class Phase { IDLE, COUNTDOWN, SHOOT, RESULT }

    // Live detection (updated on the main thread from the camera analyzer).
    var gesture by mutableStateOf(Gesture.NONE); private set
    /** Hand landmarks as normalized (0..1) points, mirrored to match the front preview. */
    var landmarks by mutableStateOf<List<Offset>>(emptyList()); private set
    var imageAspect by mutableFloatStateOf(3f / 4f); private set
    private var mirrorX = true

    fun setAspect(aspect: Float) { imageAspect = aspect }
    /** Front camera is shown selfie-style (mirror); back camera is not. */
    fun setMirror(mirror: Boolean) { mirrorX = mirror }

    // Game state.
    var phase by mutableStateOf(Phase.IDLE); private set
    var countdownValue by mutableIntStateOf(3); private set
    var playerThrow by mutableStateOf(Gesture.NONE); private set
    var aiThrow by mutableStateOf(Gesture.NONE); private set
    var outcome by mutableStateOf<RoundOutcome?>(null); private set
    var playerScore by mutableIntStateOf(0); private set
    var aiScore by mutableIntStateOf(0); private set
    var draws by mutableIntStateOf(0); private set
    var bestStreak by mutableIntStateOf(0); private set
    var xp by mutableIntStateOf(0); private set
    var streak by mutableIntStateOf(0); private set
    var displayName by mutableStateOf("Player"); private set
    val level: Int get() = xp / 100 + 1
    val levelProgress: Float get() = (xp % 100) / 100f
    val gamesPlayed: Int get() = playerScore + aiScore + draws

    fun updateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) displayName = trimmed.take(20)
    }

    // Temporal smoothing.
    private val recent = ArrayDeque<Gesture>()
    private var stable = Gesture.NONE
    private var roundJob: Job? = null

    fun onResult(result: HandLandmarkerResult) {
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            landmarks = emptyList()
            publish(Gesture.NONE)
            return
        }
        val lm = hands[0]
        // Flip x for the front camera to line up with its preview; back camera
        // is drawn straight through.
        landmarks = lm.map { Offset(if (mirrorX) 1f - it.x() else it.x(), it.y()) }
        publish(HandGestureClassifier.classify(lm))
    }

    private fun publish(raw: Gesture) {
        recent.addLast(raw)
        if (recent.size > 6) recent.removeFirst()
        val counts = recent.groupingBy { it }.eachCount()
        val bestNonNone = counts.filterKeys { it != Gesture.NONE }.maxByOrNull { it.value }
        if (bestNonNone != null && bestNonNone.value >= 3) {
            stable = bestNonNone.key
        } else if ((counts[Gesture.NONE] ?: 0) >= 4) {
            stable = Gesture.NONE
        }
        gesture = stable
    }

    val isBusy: Boolean get() = phase == Phase.COUNTDOWN || phase == Phase.SHOOT

    fun playRound() {
        if (isBusy) return
        if (phase == Phase.RESULT) reset()
        roundJob?.cancel()
        outcome = null
        playerThrow = Gesture.NONE
        aiThrow = Gesture.NONE
        roundJob = viewModelScope.launch {
            phase = Phase.COUNTDOWN
            for (n in 3 downTo 1) { countdownValue = n; delay(700) }
            phase = Phase.SHOOT
            delay(400)
            val player = gesture
            val ai = Gesture.throwable.random()
            playerThrow = player
            aiThrow = ai
            resolve(player, ai)
            phase = Phase.RESULT
        }
    }

    private fun resolve(player: Gesture, ai: Gesture) {
        if (player == Gesture.NONE) { outcome = null; return }
        when {
            player == ai -> { outcome = RoundOutcome.DRAW; draws++; xp += 2 }
            player.beats(ai) -> {
                outcome = RoundOutcome.WIN; playerScore++; xp += 10
                streak++; bestStreak = maxOf(bestStreak, streak)
            }
            else -> { outcome = RoundOutcome.LOSS; aiScore++; streak = 0 }
        }
    }

    fun reset() {
        roundJob?.cancel()
        phase = Phase.IDLE
        outcome = null
        playerThrow = Gesture.NONE
        aiThrow = Gesture.NONE
    }
}
