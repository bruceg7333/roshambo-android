package co.realmate.roshambo.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.realmate.roshambo.Gesture
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** One asynchronous friend challenge, parsed from a `challengeAction` response. */
data class AsyncChallenge(
    val id: String,
    val shareUrl: String?,
    val status: String,
    val creatorName: String,
    val responderName: String?,
    val isCreator: Boolean,
    val expiresAt: Long?,
    val creatorMove: Gesture,
    val responderMove: Gesture,
    val outcome: String?
) {
    companion object {
        fun from(data: Map<*, *>?): AsyncChallenge? {
            val id = data?.get("id") as? String ?: return null
            val status = data["status"] as? String ?: return null
            return AsyncChallenge(
                id = id,
                shareUrl = data["shareUrl"] as? String,
                status = status,
                creatorName = data["creatorName"] as? String ?: "Player",
                responderName = data["responderName"] as? String,
                isCreator = data["isCreator"] as? Boolean ?: true,
                expiresAt = (data["expiresAt"] as? Number)?.toLong(),
                creatorMove = Gesture.fromWire(data["creatorMove"] as? String),
                responderMove = Gesture.fromWire(data["responderMove"] as? String),
                outcome = data["outcome"] as? String
            )
        }
    }
}

/**
 * Drives a single async challenge, mirroring the iOS `ChallengeStore`. Owns its own
 * coroutine scope; call [dispose] when the hosting screen leaves composition.
 */
class ChallengeStore(
    private var challengeID: String?,
    private val initialMove: Gesture? = null
) {

    private val hasInitialMove = initialMove != null && initialMove != Gesture.NONE

    sealed interface Phase {
        data object Loading : Phase
        data object Preview : Phase
        data object Ready : Phase
        data class Countdown(val value: Int) : Phase
        data object Confirm : Phase
        data object Submitting : Phase
        data object Waiting : Phase
        data object Result : Phase
        data object Unavailable : Phase
    }

    var phase by mutableStateOf<Phase>(
        if (challengeID == null && !hasInitialMove) Phase.Ready else Phase.Loading
    )
        private set
    var challenge by mutableStateOf<AsyncChallenge?>(null)
        private set
    var capturedMove by mutableStateOf(Gesture.NONE)
        private set
    var history by mutableStateOf<List<AsyncChallenge>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val functions by lazy { Firebase.functions }
    private var pollingJob: Job? = null
    private var countdownJob: Job? = null
    private var submissionRequestID = UUID.randomUUID().toString().replace("-", "")

    fun start() {
        scope.launch {
            // The challenge backend requires an authenticated caller; sign in anonymously.
            if (Firebase.auth.currentUser == null) {
                runCatching { Firebase.auth.signInAnonymously().await() }
            }
            if (Firebase.auth.currentUser == null) {
                errorMessage = "Could not connect. Check your network and try again."
                phase = Phase.Unavailable
                return@launch
            }
            // Challenge-from-your-last-throw: create straight away with the passed move.
            if (challengeID == null && hasInitialMove) {
                capturedMove = initialMove!!
                submit()
                return@launch
            }
            val id = challengeID
            if (id == null) { loadHistory(); return@launch }
            try {
                val item = fetch("preview", id)
                challenge = item
                when {
                    item.status == "pending" && item.isCreator -> { phase = Phase.Waiting; startPolling() }
                    item.status == "pending" -> phase = Phase.Preview
                    item.status == "completed" -> { challenge = fetch("get", id); phase = Phase.Result }
                    else -> phase = Phase.Unavailable
                }
            } catch (e: Exception) {
                fail(e)
            }
            loadHistory()
        }
    }

    fun accept() { errorMessage = null; phase = Phase.Ready }

    /** Runs the shoot countdown, then reads the live gesture via [detectedMove]. */
    fun capture(detectedMove: () -> Gesture) {
        if (phase != Phase.Ready && phase != Phase.Confirm) return
        errorMessage = null
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (n in intArrayOf(3, 2, 1)) {
                phase = Phase.Countdown(n)
                delay(800)
            }
            val move = detectedMove()
            capturedMove = move
            if (move == Gesture.NONE) {
                errorMessage = "No hand detected. Keep your whole hand in frame and try again."
                phase = Phase.Ready
            } else {
                phase = Phase.Confirm
            }
        }
    }

    fun submit() {
        val move = capturedMove.wire ?: return
        scope.launch {
            phase = Phase.Submitting
            errorMessage = null
            try {
                val id = challengeID
                if (id != null) {
                    challenge = fetch("respond", id, move, submissionRequestID)
                    phase = Phase.Result
                } else {
                    val item = AsyncChallenge.from(
                        call(mapOf("action" to "create", "move" to move, "requestId" to submissionRequestID))
                    ) ?: throw invalidResponse()
                    challenge = item
                    challengeID = item.id
                    when (item.status) {
                        "completed" -> phase = Phase.Result
                        "pending" -> { phase = Phase.Waiting; startPolling() }
                        else -> phase = Phase.Unavailable
                    }
                }
                loadHistory()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Something went wrong."
                phase = Phase.Confirm
            }
        }
    }

    fun tryAgain() {
        capturedMove = Gesture.NONE
        submissionRequestID = UUID.randomUUID().toString().replace("-", "")
        errorMessage = null
        phase = Phase.Ready
    }

    fun cancel() {
        val id = challengeID ?: return
        scope.launch {
            try {
                call(mapOf("action" to "cancel", "id" to id))
                stopPolling()
                phase = Phase.Unavailable
                loadHistory()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun rematch() {
        stopPolling()
        challengeID = null
        challenge = null
        capturedMove = Gesture.NONE
        submissionRequestID = UUID.randomUUID().toString().replace("-", "")
        errorMessage = null
        phase = Phase.Ready
    }

    fun loadHistory() {
        scope.launch {
            try {
                val list = (call(mapOf("action" to "list"))["challenges"] as? List<*>).orEmpty()
                history = list.mapNotNull { AsyncChallenge.from(it as? Map<*, *>) }
            } catch (e: Exception) {
                if (history.isEmpty()) errorMessage = e.localizedMessage
            }
        }
    }

    fun dispose() {
        stopPolling()
        countdownJob?.cancel()
        scope.cancel()
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(4000)
                val id = challengeID ?: return@launch
                try {
                    val item = fetch("get", id)
                    challenge = item
                    if (item.status == "completed") {
                        phase = Phase.Result
                        loadHistory()
                        return@launch
                    }
                    if (item.status != "pending") { phase = Phase.Unavailable; return@launch }
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage
                }
            }
        }
    }

    private suspend fun fetch(
        action: String, id: String, move: String? = null, requestID: String? = null
    ): AsyncChallenge {
        val payload = buildMap<String, Any> {
            put("action", action); put("id", id)
            move?.let { put("move", it) }
            requestID?.let { put("requestId", it) }
        }
        return AsyncChallenge.from(call(payload)) ?: throw invalidResponse()
    }

    private suspend fun call(payload: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable("challengeAction").call(payload).await()
        return result.getData() as? Map<*, *> ?: throw invalidResponse()
    }

    private fun invalidResponse() = IllegalStateException("Invalid server response")

    private fun fail(error: Exception) {
        errorMessage = error.localizedMessage ?: "Something went wrong."
        phase = Phase.Unavailable
    }
}
