package co.realmate.roshambo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.realmate.roshambo.Gesture
import co.realmate.roshambo.RoshamboViewModel
import co.realmate.roshambo.SoundEffects
import co.realmate.roshambo.SoundEffects.Cue
import co.realmate.roshambo.challenge.AsyncChallenge
import co.realmate.roshambo.challenge.ChallengeStore
import co.realmate.roshambo.challenge.ChallengeStore.Phase

@Composable
fun ChallengeScreen(
    vm: RoshamboViewModel,
    challengeId: String?,
    initialMove: Gesture? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val store = remember(challengeId, initialMove) { ChallengeStore(challengeId, initialMove) }
    var showingHistory by remember { mutableStateOf(false) }

    LaunchedEffect(store) { store.start() }
    DisposableEffect(store) { onDispose { store.dispose() } }

    val phase = store.phase
    // Light taps on each countdown beat, matching the iOS haptics.
    LaunchedEffect(phase) {
        when (phase) {
            is Phase.Countdown -> {
                SoundEffects.play(context, when (phase.value) {
                    3 -> Cue.COUNTDOWN_3
                    2 -> Cue.COUNTDOWN_2
                    else -> Cue.COUNTDOWN_1
                })
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            is Phase.Confirm -> SoundEffects.play(context, Cue.SHOOT)
            is Phase.Result -> {
                SoundEffects.play(context, when (store.challenge?.outcome) {
                    "win" -> Cue.WIN
                    "loss" -> Cue.LOSE
                    else -> Cue.DRAW
                })
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            else -> {}
        }
    }

    val usesCamera = phase is Phase.Ready || phase is Phase.Countdown || phase is Phase.Confirm
    val topScrim = if (usesCamera) 0.35f else 0.8f

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(topScrim), Color.Black.copy(0.92f)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(onClose = onDismiss, onHistory = { showingHistory = true; store.loadHistory() })
            Spacer(Modifier.weight(1f))
            Content(store, vm)
            Spacer(Modifier.weight(1f))
            store.errorMessage?.let {
                Text(it, color = Palette.lose, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Actions(store, vm, context)
        }

        if (showingHistory) {
            HistoryOverlay(store.history) { showingHistory = false }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIcon("✕", onClose)
        Spacer(Modifier.weight(1f))
        Text("FRIEND CHALLENGE", color = Color.White, fontWeight = FontWeight.Black,
            fontSize = 13.sp, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        CircleIcon("↻", onHistory)
    }
}

@Composable
private fun CircleIcon(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(0.55f)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(glyph, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun Content(store: ChallengeStore, vm: RoshamboViewModel) {
    when (val phase = store.phase) {
        is Phase.Loading, is Phase.Submitting ->
            CircularProgressIndicator(color = Color.White)

        is Phase.Preview -> CenteredMessage(
            emoji = "⚡",
            title = "${store.challenge?.creatorName ?: "A PLAYER"}\nCHALLENGED YOU",
            subtitle = "Their move is locked. Make your throw to reveal both hands."
        )

        is Phase.Ready, is Phase.Countdown, is Phase.Confirm -> CapturePanel(store, vm, phase)

        is Phase.Waiting -> CenteredMessage(
            emoji = "🔒",
            title = "MOVE LOCKED",
            subtitle = "Share the link. Your move stays hidden until your friend responds.",
            titleColor = Palette.draw
        )

        is Phase.Result -> ResultPanel(store.challenge)

        is Phase.Unavailable -> CenteredMessage(
            emoji = "⌛",
            title = "CHALLENGE UNAVAILABLE",
            subtitle = "It may have expired, been cancelled, or already been answered."
        )
    }
}

@Composable
private fun CenteredMessage(
    emoji: String, title: String, subtitle: String, titleColor: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(emoji, fontSize = 60.sp)
        Text(title, color = titleColor, fontWeight = FontWeight.Black, fontSize = 26.sp,
            letterSpacing = 1.5.sp, textAlign = TextAlign.Center)
        Text(subtitle, color = Color.White.copy(0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CapturePanel(store: ChallengeStore, vm: RoshamboViewModel, phase: Phase) {
    val detected = vm.gesture != Gesture.NONE
    val border = if (detected) Palette.win else Palette.accent2
    Box(
        Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(30.dp))
            .border(3.dp, border, RoundedCornerShape(30.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            is Phase.Countdown ->
                Text("${phase.value}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 110.sp)
            is Phase.Confirm ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.clip(RoundedCornerShape(22.dp))
                        .background(Color.Black.copy(0.52f)).padding(24.dp)) {
                    Text(store.capturedMove.emoji, fontSize = 80.sp)
                    Text("${store.capturedMove.label.uppercase()} DETECTED",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
                }
            else ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (detected) "${vm.gesture.label.uppercase()} DETECTED" else "PLACE YOUR HAND HERE",
                        color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.5.sp)
                    Text(if (detected) vm.gesture.emoji else "✋", fontSize = 46.sp)
                }
        }
    }
}

@Composable
private fun ResultPanel(item: AsyncChallenge?) {
    val youMove = if (item?.isCreator == true) item.creatorMove else item?.responderMove ?: Gesture.NONE
    val themMove = if (item?.isCreator == true) item?.responderMove ?: Gesture.NONE else item?.creatorMove ?: Gesture.NONE
    val themName = if (item?.isCreator == true) item.responderName ?: "FRIEND" else item?.creatorName ?: "FRIEND"
    val color = when (item?.outcome) {
        "win" -> Palette.win; "loss" -> Palette.lose; else -> Palette.draw
    }
    val title = when (item?.outcome) {
        "win" -> "YOU WIN"; "loss" -> "YOU LOSE"; else -> "DRAW"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoveCard(youMove, "YOU")
            Text("VS", color = Color.White.copy(0.5f), fontWeight = FontWeight.Black, fontSize = 16.sp)
            MoveCard(themMove, themName)
        }
        Text(title, color = color, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun MoveCard(move: Gesture, title: String) {
    Column(
        Modifier.size(width = 130.dp, height = 150.dp).clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(0.09f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(move.emoji, fontSize = 58.sp)
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun Actions(store: ChallengeStore, vm: RoshamboViewModel, context: Context) {
    when (store.phase) {
        is Phase.Preview -> PrimaryButton("ACCEPT CHALLENGE") { store.accept() }
        is Phase.Ready -> PrimaryButton("START ROUND") { store.capture { vm.gesture } }
        is Phase.Confirm -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(if (store.errorMessage == null) "USE THIS MOVE" else "RETRY SUBMIT") { store.submit() }
            Text("TRY AGAIN", color = Color.White.copy(0.75f), fontWeight = FontWeight.Black, fontSize = 14.sp,
                modifier = Modifier.clickable { store.tryAgain() }.padding(8.dp))
        }
        is Phase.Waiting -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            store.challenge?.shareUrl?.let { url ->
                PrimaryButton("SHARE CHALLENGE") { shareLink(context, url) }
                Text("COPY LINK", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp,
                    modifier = Modifier.clickable { copyLink(context, url) }.padding(8.dp))
            }
            Text("CANCEL CHALLENGE", color = Palette.lose, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.clickable { store.cancel() }.padding(6.dp))
        }
        is Phase.Result -> PrimaryButton("REMATCH & SHARE") { store.rematch() }
        else -> {}
    }
}

@Composable
private fun PrimaryButton(title: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp))
            .background(Palette.fire).clickable {
                SoundEffects.play(context, Cue.TICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun HistoryOverlay(history: List<AsyncChallenge>, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.96f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CHALLENGES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Text("DONE", color = Palette.accent2, fontWeight = FontWeight.Black, fontSize = 14.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No challenges yet", color = Color.White.copy(0.6f), fontSize = 15.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(history) { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(0.07f)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                val opponent = if (item.isCreator) item.responderName ?: "WAITING…" else item.creatorName
                                Text("VS $opponent", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(item.status.uppercase(),
                                    color = if (item.status == "completed") Palette.win else Color.White.copy(0.6f),
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            if (item.status == "completed") {
                                Text((item.outcome ?: "draw").uppercase(),
                                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Roshambo Challenge")
        putExtra(Intent.EXTRA_TEXT, "I locked in my move. Can you beat me? $url")
    }
    context.startActivity(Intent.createChooser(intent, "Share challenge"))
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Roshambo Challenge", url))
}
