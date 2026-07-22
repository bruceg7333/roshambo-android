package co.realmate.roshambo.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Palette {
    val accent = Color(0xFF2185FF)   // electric blue
    val accent2 = Color(0xFF00E1FF)  // cyan
    val win = Color(0xFF5CFF70)
    val lose = Color(0xFFFF4759)
    val draw = Color(0xFFFFC72B)
    val fireHi = Color(0xFFFF9500)
    val fireLo = Color(0xFFFF2222)

    val fire = Brush.verticalGradient(listOf(fireHi, fireLo))
    val brand = Brush.verticalGradient(listOf(accent2, accent))

    fun outcomeColor(o: co.realmate.roshambo.RoundOutcome?): Color = when (o) {
        co.realmate.roshambo.RoundOutcome.WIN -> win
        co.realmate.roshambo.RoundOutcome.LOSS -> lose
        co.realmate.roshambo.RoundOutcome.DRAW -> draw
        null -> Color.White
    }
}
