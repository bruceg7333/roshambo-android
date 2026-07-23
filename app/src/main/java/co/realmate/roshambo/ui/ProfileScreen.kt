package co.realmate.roshambo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.realmate.roshambo.RoshamboViewModel
import co.realmate.roshambo.SoundEffects
import co.realmate.roshambo.SoundEffects.Cue
import co.realmate.roshambo.solana.WalletViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun ProfileScreen(
    game: RoshamboViewModel,
    wallet: WalletViewModel,
    sender: ActivityResultSender,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    LaunchedEffect(wallet.address) { if (wallet.address != null) wallet.refreshBalance() }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .background(Brush.verticalGradient(listOf(Palette.accent.copy(0.30f), Color.Black, Color.Black)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PROFILE", color = Color.White, fontWeight = FontWeight.Black,
                    fontSize = 24.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("✕", color = Color.White.copy(0.7f), fontSize = 22.sp,
                    modifier = Modifier.clickable { onClose() }.padding(8.dp))
            }

            profileCard(game)
            soundCard(context)
            walletCard(wallet, game, sender, context, uriHandler)

            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun soundCard(context: Context) {
    var enabled by remember { mutableStateOf(SoundEffects.isEnabled(context)) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("SOUND EFFECTS", color = Color.White, fontWeight = FontWeight.Black,
            fontSize = 13.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                SoundEffects.setEnabled(context, it)
                if (it) SoundEffects.play(context, Cue.TICK)
            }
        )
    }
}

@Composable
private fun walletCard(
    wallet: WalletViewModel,
    game: RoshamboViewModel,
    sender: ActivityResultSender,
    context: Context,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WALLET", color = Color.White.copy(0.6f), fontWeight = FontWeight.Black,
                fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("DEVNET", color = Palette.accent2, fontWeight = FontWeight.Black, fontSize = 10.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                    .background(Palette.accent2.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }

        val addr = wallet.address
        if (addr == null) {
            Text("Not connected", color = Color.White.copy(0.6f), fontSize = 14.sp)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Palette.brand)
                    .clickable(enabled = !wallet.connecting) { wallet.connect(sender) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (wallet.connecting) "CONNECTING…" else "CONNECT WALLET",
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        } else {
            Text("ADDRESS", color = Color.White.copy(0.45f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(
                addr, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(0.35f))
                    .clickable {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("address", addr))
                    }
                    .padding(12.dp)
            )
            Text("Tap address to copy", color = Color.White.copy(0.4f), fontSize = 10.sp)

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("BALANCE", color = Color.White.copy(0.45f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    Text(
                        wallet.balanceSol?.let { "◎ %.3f".format(it) } ?: "◎ …",
                        color = Palette.win, fontWeight = FontWeight.Black, fontSize = 26.sp
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("SKR REWARDS", color = Color.White.copy(0.45f), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    Text(
                        wallet.skrBalance?.let { "🔶 %.0f".format(it) } ?: "🔶 …",
                        color = Palette.draw, fontWeight = FontWeight.Black, fontSize = 26.sp
                    )
                }
            }
            Row {
                Spacer(Modifier.weight(1f))
                Text("REFRESH", color = Palette.accent2, fontWeight = FontWeight.Black, fontSize = 11.sp,
                    modifier = Modifier.clickable { wallet.refreshBalance() }.padding(8.dp))
                Spacer(Modifier.size(8.dp))
                Text("DISCONNECT", color = Palette.lose, fontWeight = FontWeight.Black, fontSize = 11.sp,
                    modifier = Modifier.clickable { wallet.disconnect(sender) }.padding(8.dp))
            }

            // Publish score on-chain (SPL Memo tx, signed by the wallet).
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Palette.fire)
                    .clickable(enabled = !wallet.publishing) {
                        val memo = "Roshambo | ${game.playerScore} wins | Lv ${game.level} | ${game.xp} XP"
                        wallet.publishScore(sender, memo)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (wallet.publishing) "PUBLISHING…" else "⛓ PUBLISH SCORE ON-CHAIN",
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }

            wallet.lastSignature?.let { sig ->
                Text("✓ Published on devnet", color = Palette.win, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("View on explorer ↗", color = Palette.accent2, fontSize = 12.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://explorer.solana.com/tx/$sig?cluster=devnet")
                    })
            }

            wallet.error?.let {
                Text(it, color = Palette.lose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun profileCard(game: RoshamboViewModel) {
    var name by remember { mutableStateOf(game.displayName) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(game.level, game.levelProgress)
            Spacer(Modifier.size(16.dp))
            Column {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it; game.updateName(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp),
                    cursorBrush = SolidColor(Palette.accent2)
                )
                Spacer(Modifier.size(4.dp))
                Text("LEVEL ${game.level} · ${game.gamesPlayed} GAMES",
                    color = Color.White.copy(0.55f), fontWeight = FontWeight.Black,
                    fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            statChip("WINS", "${game.playerScore}", Palette.win)
            statChip("LOSSES", "${game.aiScore}", Palette.lose)
            statChip("DRAWS", "${game.draws}", Palette.draw)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            statChip("STREAK", "🔥 ${game.streak}", Palette.accent2)
            statChip("BEST", "🏆 ${game.bestStreak}", Palette.accent2)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.statChip(label: String, value: String, tint: Color) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(0.35f)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = tint, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, color = Color.White.copy(0.55f), fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}
