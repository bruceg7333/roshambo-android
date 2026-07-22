package co.realmate.roshambo.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.realmate.roshambo.Gesture
import co.realmate.roshambo.RoundOutcome
import co.realmate.roshambo.RoshamboViewModel
import co.realmate.roshambo.solana.WalletViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun GameScreen(
    vm: RoshamboViewModel,
    walletVm: WalletViewModel,
    sender: ActivityResultSender,
    challengeId: String? = null,
    onChallengeConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var showProfile by remember { mutableStateOf(false) }
    var showChallenge by remember { mutableStateOf(false) }
    var challengeMove by remember { mutableStateOf<Gesture?>(null) }
    var useBack by remember {
        mutableStateOf(context.prefs().getBoolean("useBackCamera", false))
    }

    // An incoming challenge link opens the challenge flow straight away.
    LaunchedEffect(challengeId) { if (challengeId != null) showChallenge = true }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(vm, useBack, Modifier.fillMaxSize())

        // Scrim + vignette for legibility.
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                Color.Black.copy(0.5f), Color.Black.copy(0.18f),
                Color.Black.copy(0.52f), Color.Black.copy(0.9f)))))
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(0.48f)), radius = 1400f)))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            topHUD(vm) { showProfile = true }

            Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text("AI READY", color = Palette.lose.copy(0.85f),
                    fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 2.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val label = if (useBack) "BACK" else "FRONT"
                    Text("⟳ $label", color = Palette.accent2, fontWeight = FontWeight.Black, fontSize = 10.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(0.55f))
                            .clickable(enabled = !vm.isBusy) {
                                useBack = !useBack
                                context.prefs().edit().putBoolean("useBackCamera", useBack).apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }

            DetectionFrame(vm)

            Text("Keep your whole hand visible inside the frame",
                color = Color.White.copy(0.65f), fontSize = 11.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp))

            Spacer(Modifier.weight(1f))

            Text("ROCK · PAPER · SCISSORS", color = Color.White.copy(0.9f),
                fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.5.sp,
                modifier = Modifier.padding(bottom = 14.dp))

            PlayButton(vm)
            // Offer a challenge only once you've thrown a move to challenge with.
            if (vm.phase == RoshamboViewModel.Phase.RESULT && vm.playerThrow != Gesture.NONE) {
                ChallengeButton {
                    challengeMove = vm.playerThrow
                    showChallenge = true
                }
            }
        }

        if (showProfile) {
            ProfileScreen(vm, walletVm, sender) { showProfile = false }
        }

        if (showChallenge) {
            ChallengeScreen(
                vm = vm,
                challengeId = challengeId,
                initialMove = if (challengeId == null) challengeMove else null
            ) {
                showChallenge = false
                challengeMove = null
                onChallengeConsumed()
            }
        }
    }
}

@Composable
private fun ChallengeButton(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(top = 9.dp).height(50.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(0.58f))
            .border(1.dp, Palette.accent2.copy(0.65f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✈", fontSize = 14.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("CHALLENGE A FRIEND", color = Color.White, fontWeight = FontWeight.Black,
                fontSize = 13.sp, letterSpacing = 1.8.sp)
        }
    }
}

private fun Context.prefs() = getSharedPreferences("roshambo", Context.MODE_PRIVATE)

// MARK: - HUD

@Composable
private fun topHUD(vm: RoshamboViewModel, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(0.08f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LevelBadge(vm.level, vm.levelProgress)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                scoreLabel("YOU", vm.playerScore, Palette.accent)
                Text(" : ", color = Color.White.copy(0.65f), fontWeight = FontWeight.Black, fontSize = 22.sp)
                scoreLabel("AI", vm.aiScore, Palette.lose)
            }
            Text("SOLO MATCH", color = Color.White.copy(0.5f),
                fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(width = 40.dp, height = 44.dp).clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(0.5f)).clickable { onProfile() },
            contentAlignment = Alignment.Center
        ) { Text("🏆", fontSize = 18.sp) }
    }
}

@Composable
private fun scoreLabel(label: String, value: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = tint, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.width(4.dp))
        Text("$value", color = tint, fontWeight = FontWeight.Black, fontSize = 22.sp)
    }
}

@Composable
fun LevelBadge(level: Int, progress: Float) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            drawArc(Color.White.copy(0.2f), 0f, 360f, false, style = Stroke(stroke))
            drawArc(Palette.brand, -90f, 360f * progress.coerceAtLeast(0.02f), false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LV", color = Color.White.copy(0.6f), fontWeight = FontWeight.Black, fontSize = 8.sp)
            Text("$level", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

// MARK: - Detection frame

@Composable
private fun DetectionFrame(vm: RoshamboViewModel) {
    val detected = vm.gesture != Gesture.NONE
    val tint = if (detected) Palette.win else Palette.accent2
    val transition = rememberInfiniteTransition(label = "scan")
    val scanY by transition.animateFloat(
        initialValue = -150f, targetValue = 150f,
        animationSpec = infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "scanY"
    )

    Box(
        Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.12f))))
    ) {
        ScanGrid(Modifier.fillMaxSize())
        ScanCorners(tint, Modifier.fillMaxSize())

        if (vm.phase == RoshamboViewModel.Phase.IDLE) {
            if (vm.landmarks.isEmpty()) {
                Text("PLACE HAND INSIDE FRAME", color = Color.White.copy(0.42f),
                    fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.8.sp,
                    modifier = Modifier.align(Alignment.Center))
            }
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                    .graphicsLayer { translationY = scanY }
                    .height(2.dp)
                    .background(Brush.horizontalGradient(listOf(
                        Color.Transparent, Palette.accent2, Color.White, Palette.accent2, Color.Transparent)))
            )
        } else {
            FrameGameState(vm, Modifier.align(Alignment.Center))
        }

        // Status capsule
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                .clip(CircleShape).background(Color.Black.copy(0.72f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (detected) { Text(vm.gesture.emoji, fontSize = 18.sp); Spacer(Modifier.width(9.dp)) }
            Text(frameStatus(vm), color = Color.White.copy(0.9f),
                fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, maxLines = 1)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        }
    }
}

@Composable
private fun FrameGameState(vm: RoshamboViewModel, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (vm.phase) {
            RoshamboViewModel.Phase.COUNTDOWN ->
                Text("${vm.countdownValue}", color = Color.White,
                    fontWeight = FontWeight.Black, fontSize = 118.sp)
            RoshamboViewModel.Phase.SHOOT ->
                Text("SHOOT!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 58.sp)
            RoshamboViewModel.Phase.RESULT -> ResultView(vm)
            else -> {}
        }
    }
}

@Composable
private fun ResultView(vm: RoshamboViewModel) {
    val color = Palette.outcomeColor(vm.outcome)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            throwCard("YOU", vm.playerThrow, vm.outcome == RoundOutcome.WIN)
            Text("VS", color = Color.White.copy(0.5f), fontWeight = FontWeight.Black, fontSize = 18.sp)
            throwCard("AI", vm.aiThrow, vm.outcome == RoundOutcome.LOSS)
        }
        Text(outcomeText(vm.outcome), color = color, fontWeight = FontWeight.Black, fontSize = 26.sp)
    }
}

@Composable
private fun throwCard(title: String, gesture: Gesture, winner: Boolean) {
    Column(
        Modifier.size(width = 96.dp, height = 112.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(0.1f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(gesture.emoji, fontSize = 46.sp)
        Text(title, color = if (winner) Palette.win else Color.White.copy(0.8f),
            fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

private fun frameStatus(vm: RoshamboViewModel): String = when (vm.phase) {
    RoshamboViewModel.Phase.IDLE ->
        if (vm.gesture == Gesture.NONE) "WAITING FOR HAND" else "${vm.gesture.label.uppercase()} DETECTED"
    RoshamboViewModel.Phase.COUNTDOWN -> "HOLD YOUR GESTURE"
    RoshamboViewModel.Phase.SHOOT -> "CAPTURING HAND"
    RoshamboViewModel.Phase.RESULT -> outcomeText(vm.outcome)
}

private fun outcomeText(o: RoundOutcome?): String = when (o) {
    RoundOutcome.WIN -> "YOU WIN!"
    RoundOutcome.LOSS -> "YOU LOSE"
    RoundOutcome.DRAW -> "DRAW"
    null -> "NO HAND"
}

@Composable
private fun ScanGrid(modifier: Modifier) {
    Canvas(modifier) {
        val step = 28.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(Palette.accent2.copy(0.1f), Offset(x, 0f), Offset(x, size.height), 0.7f); x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(Palette.accent2.copy(0.1f), Offset(0f, y), Offset(size.width, y), 0.7f); y += step
        }
    }
}

@Composable
private fun ScanCorners(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val inset = 18.dp.toPx(); val len = 44.dp.toPx(); val w = 4.dp.toPx()
        val s = androidx.compose.ui.graphics.drawscope.Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun corner(cx: Float, cy: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(cx, cy), Offset(cx + dx, cy), w, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, Offset(cx, cy), Offset(cx, cy + dy), w, androidx.compose.ui.graphics.StrokeCap.Round)
        }
        corner(inset, inset, len, len)
        corner(size.width - inset, inset, -len, len)
        corner(inset, size.height - inset, len, -len)
        corner(size.width - inset, size.height - inset, -len, -len)
    }
}

// MARK: - Play button

@Composable
private fun PlayButton(vm: RoshamboViewModel) {
    val shape = RoundedCornerShape(22.dp)
    val enabled = !vm.isBusy
    val label = if (vm.phase == RoshamboViewModel.Phase.RESULT) "PLAY AGAIN" else "START ROUND"
    Box(
        Modifier.fillMaxWidth().height(74.dp).clip(shape)
            .background(if (enabled) Palette.fire else Brush.verticalGradient(listOf(Color.Gray, Color.DarkGray)))
            .clickable(enabled = enabled) { vm.playRound() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp,
                letterSpacing = 3.sp, textAlign = TextAlign.Center)
        }
    }
}
