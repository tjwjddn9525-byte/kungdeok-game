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
import com.kungdeok.game.ui.formatDuration
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.kungdeok.game.StepTracker
import com.kungdeok.game.DawnAlarm
import com.kungdeok.game.logic.epochDay
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
class OverlayService : LifecycleService(), CatOverlayCallbacks, SavedStateRegistryOwner {

    // 오버레이 메뉴 창(Compose)이 저장 상태를 쓸 수 있게 서비스가 SavedStateRegistryOwner 노릇을 한다.
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    /** 고양이를 톡 하면 머리 위에 뜨는 작은 메뉴 */
    private var menu: CatMenu? = null
    /** 메뉴가 그리는 게임 상태 (Compose 가 지켜봄) */
    private val menuState = androidx.compose.runtime.mutableStateOf<GameState?>(null)

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

    /** 합체 중(SPEC 6-3)에는 쿵덕몬 한 마리만 돌아다닌다. */
    private var fusionView: CatOverlay? = null
    private var fused = false
    private val allViews: List<CatOverlay> get() = cats.values + listOfNotNull(fusionView)
    private val activeViews: List<CatOverlay> get() = if (fused) listOfNotNull(fusionView) else cats.values.toList()

    /** 쿵덕몬: 톡 = 돌봄 메뉴 (두 마리 모두에게) */
    private val fusionCallbacks = object : CatOverlayCallbacks {
        override fun onTap(cat: CatId) = handleTap(null)

        override fun onLongPress(cat: CatId) = this@OverlayService.onLongPress(cat)
        override fun onDragStart(cat: CatId) = this@OverlayService.onDragStart(cat)
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
            lastFrameNanos = frameTimeNanos
            checkFusion()
            checkDawn(frameTimeNanos)
            if (!fused) director?.step(dt)
            activeViews.forEach { it.step(dt) }
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
            OverlaySettings.KEY_SIZE -> allViews.forEach { it.setSizeScale(settings.size.scale) }
            OverlaySettings.KEY_OPACITY -> allViews.forEach { it.setOpacity(settings.opacity) }
        }
    }

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        settings = OverlaySettings(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!startInForeground() || !OverlaySettings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        for (id in CatId.entries) {
            cats[id] = CatOverlay(this, wm, id, this).apply {
                setSizeScale(settings.size.scale)
                setOpacity(settings.opacity)
            }
        }
        fusionView = CatOverlay(this, wm, CatId.KUNG, fusionCallbacks, fusion = true).apply {
            setSizeScale(settings.size.scale)
            setOpacity(settings.opacity)
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
                menuState.value = s
                director?.friendship = s.game.friendship
                val now = System.currentTimeMillis()
                cats.forEach { (id, view) ->
                    view.update(s.cat(id), now)
                    view.hasBox = "box" in s.game.placed
                }
                fusionView?.update(s.kung, now)
                if (!screenReady) {
                    screenReady = true
                    fused = s.game.isFused(now)
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
        menu?.close(notify = false)
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
        allViews.forEach { it.setScreen(w, bottom, firstTime) }
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
        activeViews.forEach { runCatching { it.attach() } }
    }

    private fun hideCats() {
        allViews.forEach { runCatching { it.detach() } }
    }

    private var lastDawnCheck = 0L
    private var dawnFussDay = 0L

    /** 새벽 5~8시에 처음 화면을 켜면 두 마리가 밥 달라고 우다다 (하루 한 번) */
    private fun checkDawn(frameTimeNanos: Long) {
        if (frameTimeNanos - lastDawnCheck < 5_000_000_000L) return
        lastDawnCheck = frameTimeNanos
        if (!settings.dawnWake || fused || settings.hidden) return
        val hour = java.time.LocalTime.now().hour
        if (hour < DawnAlarm.START_HOUR || hour >= 8) return
        val today = epochDay(System.currentTimeMillis())
        if (dawnFussDay == today) return
        dawnFussDay = today
        cats.values.forEach { it.dawnCall() }
        lifecycleScope.launch { repo.markDawn(today) }
    }

    /** 합체가 시작되거나 30분이 끝나면 화면 위 모습을 바꾼다. */
    private fun checkFusion() {
        val want = lastState?.game?.isFused(System.currentTimeMillis()) == true
        if (want == fused) return
        val f = fusionView ?: return
        val kung = cats.getValue(CatId.KUNG)
        val nodeok = cats.getValue(CatId.NODEOK)
        fused = want
        if (fused) {
            f.moveCenterTo((kung.centerX + nodeok.centerX) / 2f)
        } else {
            kung.moveCenterTo(f.centerX - kung.widthPx * 0.6f)
            nodeok.moveCenterTo(f.centerX + nodeok.widthPx * 0.6f)
        }
        if (!settings.hidden && screenReady) {
            hideCats()
            showCats()
        }
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

    /** 톡: 그 고양이 머리 위에 돌봄 메뉴. 열려 있으면 닫기. */
    override fun onTap(cat: CatId) = handleTap(cat)

    /** [cat] 이 null 이면 합체한 쿵덕몬 (둘 다) */
    private fun handleTap(cat: CatId?) {
        val m = menu
        if (m != null) {
            // 같은 고양이를 다시 누르면 닫기 (바깥 터치로 방금 닫힌 경우도 포함)
            val justClosed = System.currentTimeMillis() - m.closedByOutsideAt < 400
            if ((m.isOpen || justClosed) && m.target == cat) {
                m.close()
                return
            }
        }
        openMenu(cat)
    }

    private fun viewOf(cat: CatId?): CatOverlay? = if (cat == null) fusionView else cats[cat]

    private fun openMenu(cat: CatId?) {
        val view = viewOf(cat) ?: return
        val m = menu ?: CatMenu(
            this, wm, this, menuState,
            onAction = { c, action -> doCare(c, action) },
            onOpenApp = { onLongPress(CatId.KUNG) },
            onClosed = { allViews.forEach { it.held = false } },
        ).also { menu = it }
        allViews.forEach { it.held = false }
        view.held = true
        // 머리 위(말풍선 자리)에 띄워서 몸을 가리지 않는다. 자리가 없으면 발 아래로.
        val gap = resources.displayMetrics.density * 4
        m.open(cat, view.centerX, view.spriteTopY - gap, view.windowBottomY + gap, screenWidth)
    }

    /** 메뉴에서 고른 돌봄. 화면 위 고양이가 그 자리에서 반응한다. */
    private fun doCare(cat: CatId?, action: CareAction) {
        val targets = cat?.let { listOf(it) } ?: CatId.entries
        lifecycleScope.launch {
            val outcome = repo.care(targets, action)
            outcome.jealous?.let { cats[it]?.showTempBubble("흥") }
            for (id in targets) {
                val view = (if (cat == null) fusionView else cats[id]) ?: continue
                when (val r = outcome.results[id]) {
                    is CareResult.Done -> react(view, action)
                    is CareResult.Cooldown -> view.showTempBubble("⏳ ${formatDuration(r.remainingMs)}")
                    CareResult.Asleep -> view.showTempBubble("쿨쿨…")
                    null -> Unit
                }
                if (cat == null) break // 쿵덕몬은 한 번만 반응
            }
        }
    }

    private fun react(view: CatOverlay, action: CareAction) {
        when (action) {
            CareAction.PET -> view.showHeart()
            CareAction.FEED -> {
                view.forceBehavior(Behavior.KNEAD, 2.5f) // 오물오물
                view.showTempBubble("🍚 냠냠", 2.5f)
            }
            CareAction.SNACK -> {
                view.forceBehavior(Behavior.KNEAD, 1.8f)
                view.showTempBubble("🍤 냠!", 2f)
            }
            CareAction.PLAY -> {
                view.forceBehavior(Behavior.ZOOM, 2.5f) // 신나서 우다다
                view.showTempBubble("🧶 신나!", 2.5f)
            }
            CareAction.BRUSH -> {
                view.showHeart()
                view.showTempBubble("✨ 골골", 2.5f)
            }
            CareAction.SLEEP -> view.showTempBubble("🌙 잘 자…", 2.5f)
        }
    }

    override fun onDragStart(cat: CatId) {
        menu?.close()
    }

    override fun onLongPress(cat: CatId) {
        menu?.close()
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
            .setContentText("톡: 돌봄 메뉴 · 꾹: 게임 열기 · 드래그: 옮기기")
            .setContentIntent(Notifications.openAppIntent(this))
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, if (hidden) "보이기" else "숨기기", toggle)
            .addAction(0, "끄기", stop)
            .build()
    }

    /**
     * 포그라운드로 전환한다. 안드로이드 15 는 시스템이 백그라운드에서 서비스를 되살릴 때
     * (오버레이 창이 아직 없으면) 이걸 막으므로, 실패하면 false 를 돌려주고 조용히 멈춘다.
     * 앱을 열면 MainActivity 가 다시 켠다.
     */
    private fun startInForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException 포함
            false
        } catch (e: SecurityException) {
            false
        }
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
