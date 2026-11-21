package com.kungdeok.game.overlay

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kungdeok.game.KungdeokApp
import com.kungdeok.game.MainActivity
import com.kungdeok.game.Notifications
import com.kungdeok.game.R
import com.kungdeok.game.StepTracker
import com.kungdeok.game.data.GameState
import com.kungdeok.game.logic.CareAction
import com.kungdeok.game.logic.CareResult
import com.kungdeok.game.model.CatId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 화면 위에 두 마리를 띄우는 포그라운드 서비스 (SPEC 8).
 * 알림창에 숨기기/보이기 버튼이 있고, 화면이 꺼지면 애니메이션을 멈춘다.
 */
class OverlayService : LifecycleService(), CatOverlayCallbacks {

    private val repo by lazy { (application as KungdeokApp).repository }
    private lateinit var settings: OverlaySettings
    private lateinit var wm: WindowManager
    private val cats = mutableMapOf<CatId, CatOverlay>()
    private var lastState: GameState? = null
    private var screenReady = false
    private var running = false
    private var lastFrameNanos = 0L
    private var tickJob: Job? = null
    private var director: RelationshipDirector? = null
    private var screenWidth = 0
    private val steps by lazy { StepTracker(this, lifecycleScope) }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
            lastFrameNanos = frameTimeNanos
            director?.step(dt)
            cats.values.forEach { it.step(dt) }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> pause()
                Intent.ACTION_SCREEN_ON -> resume()
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            OverlaySettings.KEY_SIZE -> cats.values.forEach { it.setSizeScale(settings.size.scale) }
            OverlaySettings.KEY_OPACITY -> cats.values.forEach { it.setOpacity(settings.opacity) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = OverlaySettings(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()

        if (!OverlaySettings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        for (id in CatId.entries) {
            cats[id] = CatOverlay(this, wm, id, this).apply {
                setSizeScale(settings.size.scale)
                setOpacity(settings.opacity)
            }
        }
        steps.start()
        director = RelationshipDirector(cats.getValue(CatId.KUNG), cats.getValue(CatId.NODEOK)) { screenWidth }
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // DB 가 비어 있으면 state 가 안 나오므로 한 번 계산해 둔다.
        lifecycleScope.launch { repo.tick() }
        lifecycleScope.launch {
            repo.state.collect { s ->
                lastState = s
                director?.friendship = s.game.friendship
                val now = System.currentTimeMillis()
                cats.forEach { (id, view) -> view.update(s.cat(id), now) }
                if (!screenReady) {
                    screenReady = true
                    applyScreen(firstTime = true)
                    if (!settings.hidden) showCats()
                    if (isScreenOn()) resume()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE -> {
                settings.hidden = !settings.hidden
                if (settings.hidden) hideCats() else showCats()
                updateNotification()
            }
            ACTION_STOP -> {
                settings.enabled = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyScreen(firstTime = false)
    }

    override fun onDestroy() {
        if (cats.isNotEmpty()) steps.stop()
        pause()
        hideCats()
        if (::settings.isInitialized) settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    // ---- 화면 크기 ----

    private fun applyScreen(firstTime: Boolean) {
        val (w, bottom) = screenSize()
        screenWidth = w
        cats.values.forEach { it.setScreen(w, bottom, firstTime) }
    }

    /** (화면 폭, 고양이가 서는 바닥 y). 내비게이션 바 바로 위를 바닥으로 삼는다. */
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.navigationBars(),
            )
            metrics.bounds.width() to metrics.bounds.height() - insets.bottom
        } else {
            val real = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(real)
            val usable = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(usable)
            real.widthPixels to usable.heightPixels
        }
    }

    // ---- 보이기/숨기기, 애니메이션 ----

    private fun showCats() {
        if (!screenReady) return
        cats.values.forEach { runCatching { it.attach() } }
    }

    private fun hideCats() {
        cats.values.forEach { runCatching { it.detach() } }
    }

    private fun isScreenOn(): Boolean = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive

    private fun resume() {
        if (running || !screenReady) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
        // 화면이 켜져 있는 동안 1분마다 시간 경과를 반영 (말풍선, 진화 알림 갱신)
        tickJob = lifecycleScope.launch {
            while (true) {
                repo.tick()
                delay(60_000)
            }
        }
    }

    private fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        tickJob?.cancel()
        tickJob = null
    }

    // ---- 터치 ----

    override fun onTap(cat: CatId) {
        lifecycleScope.launch {
            val outcome = repo.care(listOf(cat), CareAction.PET)
            outcome.jealous?.let { cats[it]?.showTempBubble("흥") }
            val result = outcome.results[cat]
            val view = cats[cat] ?: return@launch
            when (result) {
                is CareResult.Done -> view.showHeart()
                CareResult.Asleep -> view.showTempBubble("쿨쿨…")
                else -> Unit
            }
        }
    }

    override fun onLongPress(cat: CatId) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    // ---- 알림 ----

    private fun buildNotification(): Notification {
        val hidden = settings.hidden
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setContentTitle(if (hidden) "쿵과 노덕이 숨어 있어요" else "쿵과 노덕이 화면 위에 있어요")
            .setContentText("톡: 쓰다듬기 · 꾹: 게임 열기 · 드래그: 옮기기")
            .setContentIntent(Notifications.openAppIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, if (hidden) "보이기" else "숨기기", toggle)
            .addAction(0, "끄기", stop)
            .build()
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    @android.annotation.SuppressLint("MissingPermission") // canPost 로 확인함
    private fun updateNotification() {
        if (Notifications.canPost(this)) {
            runCatching {
                androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_TOGGLE = "com.kungdeok.game.overlay.TOGGLE"
        const val ACTION_STOP = "com.kungdeok.game.overlay.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
