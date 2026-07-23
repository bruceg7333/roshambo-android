package co.realmate.roshambo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundEffects {
    const val enabledKey = "soundEffectsEnabled"

    enum class Cue(val resource: Int) {
        TICK(R.raw.button_tick),
        START(R.raw.round_start),
        COUNTDOWN_3(R.raw.countdown_3),
        COUNTDOWN_2(R.raw.countdown_2),
        COUNTDOWN_1(R.raw.countdown_1),
        SHOOT(R.raw.shoot),
        WIN(R.raw.win),
        LOSE(R.raw.lose),
        DRAW(R.raw.draw),
        DETECTED(R.raw.hand_detected)
    }

    private var pool: SoundPool? = null
    private val sounds = mutableMapOf<Cue, Int>()

    fun initialize(context: Context) {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        Cue.entries.forEach { sounds[it] = pool!!.load(context, it.resource, 1) }
    }

    fun play(context: Context, cue: Cue) {
        if (!isEnabled(context)) return
        initialize(context.applicationContext)
        sounds[cue]?.let { pool?.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("roshambo", Context.MODE_PRIVATE)
            .getBoolean(enabledKey, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("roshambo", Context.MODE_PRIVATE)
            .edit().putBoolean(enabledKey, enabled).apply()
    }
}
