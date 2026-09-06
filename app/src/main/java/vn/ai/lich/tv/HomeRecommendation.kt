package vn.ai.lich.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import java.time.LocalDate

/**
 * Best-effort Android TV Home channel. Launchers decide whether/how to surface it.
 * It gives the user a glanceable lunar date card without relying on a phone-style widget.
 */
object HomeRecommendation {
    private const val PREF = "lichai_tv_home"
    private const val CHANNEL_ID = "channel_id"

    fun publish(context: Context) {
        runCatching {
            val helper = PreviewChannelHelper(context)
            val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            var channelId = prefs.getLong(CHANNEL_ID, -1L)
            if (channelId < 0) {
                val channel = androidx.tvprovider.media.tv.PreviewChannel.Builder()
                    .setDisplayName("Lịch AI · Hôm nay")
                    .setDescription("Âm lịch hôm nay")
                    .setAppLinkIntentUri(Uri.parse("lichai-tv://today"))
                    .build()
                channelId = helper.publishChannel(channel)
                prefs.edit().putLong(CHANNEL_ID, channelId).apply()
            }
            val today = LocalDate.now()
            val lunar = VietnameseLunar.fromSolar(today)
            val intent = Intent(context, MainActivity::class.java)
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle("Hôm nay ${today.dayOfMonth}/${today.monthValue} · Âm ${lunar.day}/${lunar.month}")
                .setDescription("${VietnameseLunar.yearCanChi(lunar.year)} · Bấm OK để mở Lịch AI")
                .setIntent(intent)
                .build()
            helper.publishPreviewProgram(program)
        }
    }
}
