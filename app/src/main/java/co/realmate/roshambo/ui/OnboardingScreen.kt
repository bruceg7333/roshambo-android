package co.realmate.roshambo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.realmate.roshambo.Gesture
import co.realmate.roshambo.RoshamboViewModel
import kotlinx.coroutines.delay

/** Camera-first onboarding: detect a hand, learn the rules, practice each move. */
@Composable
fun OnboardingScreen(vm: RoshamboViewModel, onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var practiceIndex by remember { mutableIntStateOf(0) }
    val targets = Gesture.throwable
    val cameraStep = step == 0 || step == 2

    // Advance practice when the target gesture is held briefly.
    LaunchedEffect(vm.gesture, step, practiceIndex) {
        if (step == 2 && practiceIndex < targets.size && vm.gesture == targets[practiceIndex]) {
            delay(450)
            if (vm.gesture == targets[practiceIndex]) {
                practiceIndex++
                if (practiceIndex >= targets.size) { delay(300); step = 3 }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraView(vm, useBack = false, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                Color.Black.copy(if (cameraStep) 0.45f else 0.9f),
                Color.Black.copy(if (cameraStep) 0.75f else 0.95f)))))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress bar (4 segments)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { i ->
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape)
                        .background(if (i <= step) Palette.accent2 else Color.White.copy(0.2f)))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (step) {
                    0 -> cameraStepView(vm)
                    1 -> rulesStepView()
                    2 -> practiceStepView(vm, targets, practiceIndex)
                    else -> readyStepView()
                }
            }

            if (step == 2) {
                Text("SKIP PRACTICE", color = Color.White.copy(0.65f),
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clickable { step = 3 }.padding(8.dp))
            }
            primaryButton(step, vm) {
                when (step) {
                    3 -> onFinish()
                    else -> step++
                }
            }
        }
    }
}

@Composable
private fun cameraStepView(vm: RoshamboViewModel) {
    val detected = vm.landmarks.isNotEmpty()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(
            Modifier.size(130.dp).clip(CircleShape)
                .background(Color.White.copy(0.08f)),
            contentAlignment = Alignment.Center
        ) { Text(if (detected) "🖐️" else "👀", fontSize = 52.sp) }
        Text(if (detected) "HAND DETECTED" else "SHOW US YOUR HAND",
            color = if (detected) Palette.win else Color.White,
            fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 2.sp,
            textAlign = TextAlign.Center)
        Text(if (detected) "Perfect. Keep your hand inside the frame while you play."
        else "Hold your hand in front of the camera, palm facing the screen.",
            color = Color.White.copy(0.75f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rulesStepView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Text("THREE MOVES.\nONE WINNER.", color = Color.White, fontWeight = FontWeight.Black,
            fontSize = 30.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moveCard(Gesture.ROCK, "SCISSORS")
            moveCard(Gesture.PAPER, "ROCK")
            moveCard(Gesture.SCISSORS, "PAPER")
        }
        Text("Show your move when the countdown ends.",
            color = Color.White.copy(0.7f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun moveCard(g: Gesture, beats: String) {
    Column(
        Modifier.width(100.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.08f)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(g.emoji, fontSize = 40.sp)
        Text(g.label.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text("BEATS $beats", color = Palette.accent2, fontWeight = FontWeight.Black, fontSize = 8.sp)
    }
}

@Composable
private fun practiceStepView(vm: RoshamboViewModel, targets: List<Gesture>, index: Int) {
    val target = targets[index.coerceAtMost(targets.size - 1)]
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("TRY IT YOURSELF", color = Color.White.copy(0.7f),
            fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp)
        Text(target.emoji, fontSize = 96.sp)
        Text("SHOW ${target.label.uppercase()}", color = if (vm.gesture == target) Palette.win else Color.White,
            fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            targets.forEachIndexed { i, g ->
                Box(
                    Modifier.size(width = 56.dp, height = 48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (i < index) Palette.win.copy(0.22f) else Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center
                ) { Text(if (i < index) "✓" else g.emoji, fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun readyStepView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("⚡", fontSize = 70.sp)
        Text("READY TO THROW?", color = Color.White, fontWeight = FontWeight.Black,
            fontSize = 30.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        Text("Tap START, wait for 3 · 2 · 1, then show any move when you see SHOOT.",
            color = Color.White.copy(0.7f), fontSize = 16.sp, textAlign = TextAlign.Center)
        Text("START  →  3 · 2 · 1  →  ✊ ✋ ✌️", color = Palette.accent2,
            fontWeight = FontWeight.Black, fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.08f))
                .padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

@Composable
private fun primaryButton(step: Int, vm: RoshamboViewModel, onClick: () -> Unit) {
    val enabled = step != 2 || false // step 2 auto-advances via practice
    val label = when (step) {
        0 -> "CONTINUE"
        1 -> "START PRACTICE"
        2 -> "COMPLETE ALL MOVES"
        else -> "START FIRST ROUND"
    }
    val active = step != 2
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Palette.fire else Brush.verticalGradient(listOf(Color.Gray, Color.DarkGray)))
            .then(if (active) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
    }
}
