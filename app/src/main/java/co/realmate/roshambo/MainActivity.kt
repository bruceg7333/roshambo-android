package co.realmate.roshambo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import co.realmate.roshambo.ui.GameScreen
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    private lateinit var sender: ActivityResultSender
    // Challenge id from an incoming https://…/challenge/<id> deep link, if any.
    private var challengeId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must be created during onCreate (registers an activity-result launcher).
        sender = ActivityResultSender(this)
        challengeId = challengeIdFrom(intent)
        SoundEffects.initialize(applicationContext)
        enableEdgeToEdge()
        setContent { App(sender, challengeId) { challengeId = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        challengeIdFrom(intent)?.let { challengeId = it }
    }

    /** Extract a 32-char challenge id from a `/challenge/<id>` app link. */
    private fun challengeIdFrom(intent: Intent?): String? {
        val uri: Uri = intent?.data ?: return null
        val segments = uri.pathSegments
        return if (segments.size == 2 && segments[0] == "challenge" && segments[1].length == 32)
            segments[1] else null
    }
}

@Composable
private fun App(
    sender: ActivityResultSender,
    challengeId: String?,
    onChallengeConsumed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (granted) {
            val vm: RoshamboViewModel = viewModel()
            val walletVm: co.realmate.roshambo.solana.WalletViewModel = viewModel()
            val prefs = context.getSharedPreferences("roshambo", Context.MODE_PRIVATE)
            var onboarded by remember { mutableStateOf(prefs.getBoolean("didOnboard", false)) }
            if (onboarded) {
                GameScreen(vm, walletVm, sender, challengeId, onChallengeConsumed)
            } else {
                co.realmate.roshambo.ui.OnboardingScreen(vm) {
                    prefs.edit().putBoolean("didOnboard", true).apply()
                    onboarded = true
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("✊ ✋ ✌️", fontSize = 48.sp)
                Text(
                    "Roshambo needs your camera to read your hand gestures. It's processed on-device.",
                    color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
                androidx.compose.material3.Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) }
                ) {
                    Text("ENABLE CAMERA", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
