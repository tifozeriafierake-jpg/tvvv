package com.eightk.tv.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import coil.load
import com.eightk.tv.data.Config
import com.eightk.tv.data.XtreamClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PlayItem(val title: String, val url: String, val streamId: Int? = null, val logo: String? = null)

/** Mban listën që po luhet (kanalet ose episodet) */
object Playback {
    var items: List<PlayItem> = emptyList()
        private set
    var live = false
        private set
    var client: XtreamClient? = null
        private set

    fun open(ctx: Context, client: XtreamClient, list: List<PlayItem>, index: Int, live: Boolean) {
        items = list
        this.live = live
        this.client = client
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).putExtra("index", index))
    }
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var info: View
    private lateinit var logo: ImageView
    private lateinit var number: TextView
    private lateinit var name: TextView
    private lateinit var epg: TextView
    private lateinit var desc: TextView
    private lateinit var next: TextView
    private lateinit var progress: ProgressBar
    private var epgJob: Job? = null
    private var index = 0
    private var triedTs = false
    private var digits = ""
    private val handler = Handler(Looper.getMainLooper())
    private val hideInfo = Runnable { info.visibility = View.GONE }
    private val jumpToNumber = Runnable {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n in 1..items.size) play(n - 1) else showText("Kanali nuk ekziston", "")
    }

    private val items get() = Playback.items
    private val live get() = Playback.live

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (items.isEmpty()) { finish(); return }
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0))
            .coerceIn(0, items.lastIndex)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()

        view = PlayerView(this).apply {
            player = this@PlayerActivity.player
            useController = !live
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            keepScreenOn = true
        }
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        logo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        number = TextView(this).apply {
            setTextColor(0xFFE4252B.toInt()); textSize = 40f; typeface = Typeface.DEFAULT_BOLD
            setPadding(px(20), 0, px(20), 0)
        }
        name = TextView(this).apply { setTextColor(Color.WHITE); textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
        epg = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; maxLines = 1; typeface = Typeface.DEFAULT_BOLD }
        desc = TextView(this).apply { setTextColor(0xFFB4B9C2.toInt()); textSize = 15f; maxLines = 2 }
        next = TextView(this).apply { setTextColor(0xFF8E8E95.toInt()); textSize = 15f; maxLines = 1 }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xFF8E7CFF.toInt())
            progressBackgroundTintList = ColorStateList.valueOf(0x40FFFFFF)
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(epg, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = px(6) })
            addView(progress, LinearLayout.LayoutParams(px(520), px(6)).apply { topMargin = px(8); bottomMargin = px(8) })
            addView(desc, LinearLayout.LayoutParams(px(820), WRAP_CONTENT))
            addView(next, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = px(6) })
        }
        info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(48), px(60), px(48), px(36))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xEE000000.toInt(), 0x00000000),
            )
            addView(logo, LinearLayout.LayoutParams(px(150), px(84)))
            addView(number)
            addView(texts)
            visibility = View.GONE
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(info, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        }
        setContentView(root)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val url = items[index].url
                // Disa serverë nuk japin HLS: provo formatin .ts
                if (live && !triedTs && url.endsWith(".m3u8")) {
                    triedTs = true
                    start(url.removeSuffix(".m3u8") + ".ts")
                    return
                }
                showText("Nuk u hap: ${items[index].title}", error.errorCodeName, 8000)
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Episodi tjetër automatikisht
                if (state == Player.STATE_ENDED && !live && index < items.lastIndex) play(index + 1)
            }
        })
        play(index)
    }

    private fun play(i: Int) {
        index = i
        triedTs = false
        start(items[i].url)
        showChannel()
    }

    private fun showChannel() {
        val cur = items[index]
        logo.visibility = if (cur.logo != null) View.VISIBLE else View.GONE
        cur.logo?.let { url -> logo.load(url) }
        number.visibility = if (live) View.VISIBLE else View.GONE
        number.text = "${index + 1}"
        name.text = cur.title
        setEpgVisible(false)
        epgJob?.cancel()
        val c = Playback.client
        val id = cur.streamId
        if (live && c != null && id != null) {
            epg.text = "Duke ngarkuar programin…"
            epg.visibility = View.VISIBLE
            epgJob = lifecycleScope.launch {
                val list = runCatching { c.epg(id) }.getOrNull().orEmpty()
                val now = list.getOrNull(0)
                val nx = list.getOrNull(1)
                if (now == null) {
                    epg.text = "Nuk ka informacion për programin"
                    return@launch
                }
                setEpgVisible(true)
                epg.text = "Tani:  ${t(now.start)} - ${t(now.end)}   ${now.title}"
                desc.text = now.desc
                desc.visibility = if (now.desc.isBlank()) View.GONE else View.VISIBLE
                val span = (now.end - now.start).coerceAtLeast(1)
                progress.progress = (((System.currentTimeMillis() / 1000 - now.start) * 1000) / span).toInt().coerceIn(0, 1000)
                next.text = nx?.let { "Më pas:  ${t(it.start)}   ${it.title}" } ?: ""
                next.visibility = if (nx == null) View.GONE else View.VISIBLE
            }
        }
        reveal(7000)
    }

    private fun t(sec: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))

    private fun setEpgVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.GONE
        epg.visibility = vis; desc.visibility = vis; next.visibility = vis; progress.visibility = vis
    }

    private fun showText(title: String, sub: String, ms: Long = 4000) {
        logo.visibility = View.GONE
        number.visibility = View.GONE
        name.text = title
        setEpgVisible(false)
        epg.text = sub
        epg.visibility = if (sub.isBlank()) View.GONE else View.VISIBLE
        reveal(ms)
    }

    private fun start(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun zap(step: Int) {
        val n = items.size
        play(((index + step) % n + n) % n)
    }

    private fun reveal(ms: Long) {
        info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, ms)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (live && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(+1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
                    showChannel(); return true
                }
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                    digits += (event.keyCode - KeyEvent.KEYCODE_0).toString()
                    showText("Kanali: $digits", "")
                    handler.removeCallbacks(jumpToNumber)
                    handler.postDelayed(jumpToNumber, 1500)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) player.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
    }
}
