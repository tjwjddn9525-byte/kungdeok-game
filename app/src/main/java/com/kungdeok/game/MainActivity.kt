package com.kungdeok.game

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import com.kungdeok.game.data.GameState
import com.kungdeok.game.model.CatId
import com.kungdeok.game.model.Form
import com.kungdeok.game.ui.EvolutionScreen
import com.kungdeok.game.ui.GiftDialog
import com.kungdeok.game.ui.DexScreen
import com.kungdeok.game.ui.ShopScreen
import com.kungdeok.game.ui.TowerScreen
import com.kungdeok.game.logic.DailyRules
import com.kungdeok.game.logic.FusionRules
import com.kungdeok.game.ui.FusionScreen
import com.kungdeok.game.ui.SettingsScreen
import com.kungdeok.game.overlay.OverlayService
import com.kungdeok.game.overlay.OverlaySettings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kungdeok.game.ui.KungdeokTheme
import com.kungdeok.game.ui.MainViewModel
import com.kungdeok.game.ui.RoomScreen
import com.kungdeok.game.ui.StatusScreen
import com.kungdeok.game.ui.Target

enum class Tab(val label: String, val icon: String) {
    ROOM("방", "🏠"),
    STATUS("상태", "📊"),
    DEX("도감", "📖"),
    TOWER("캣타워", "🗼"),
    SHOP("상점", "🛒"),
    SETTINGS("설정", "⚙️"),
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val steps by lazy { StepTracker(this, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KungdeokTheme { App(vm) } }
        // 알림(진화 알림, 숨기기 버튼)과 걸음 수 권한을 한 번에 묻는다.
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this@MainActivity)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= 29 && !StepTracker.hasPermission(this@MainActivity)) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (wanted.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { steps.start() }
                .launch(wanted.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        startOverlayIfEnabled()
        steps.start()
        DawnAlarm.schedule(this)
    }

    override fun onPause() {
        steps.stop()
        super.onPause()
    }

    private fun startOverlayIfEnabled() {
        val settings = OverlaySettings(this)
        if (settings.enabled && OverlaySettings.canDrawOverlays(this)) OverlayService.start(this)
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.ROOM) }
    var target by rememberSaveable { mutableStateOf(Target.BOTH) }
    val snackbar = remember { SnackbarHostState() }
    val evolution by vm.evolution.collectAsStateWithLifecycle()
    val fusion by vm.fusion.collectAsStateWithLifecycle()
    val battle by vm.battle.collectAsStateWithLifecycle()
    val reward by vm.reward.collectAsStateWithLifecycle()
    // 권한 화면에서 돌아올 때마다 다시 확인한다.
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canDrawOverlays = remember(lifecycle) { OverlaySettings.canDrawOverlays(context) }
    val notificationsAllowed = remember(lifecycle) { Notifications.canPost(context) }

    LaunchedEffect(Unit) {
        vm.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val s = state
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (tab) {
                    Tab.ROOM -> {
                        // 합체 중에는 돌봄이 두 마리 모두에게 간다.
                        val t = if (s.game.isFused(now)) Target.BOTH else target
                        RoomScreen(s, now, t, { target = it }, { vm.care(t.cats, it) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EvolveButtons(s) { vm.evolve(it) }
                                if (FusionRules.block(s, now, vm.today()) == null) {
                                    Button(onClick = vm::fuse, modifier = Modifier.padding(4.dp)) {
                                        Text("💫 합체! (하루 한 번, 30분)")
                                    }
                                }
                            }
                        }
                    }
                    Tab.STATUS -> StatusScreen(s, now, vm::useBag)
                    Tab.TOWER -> TowerScreen(s, now, battle, reward, vm::startBattle, vm::battleAction, vm::leaveBattle)
                    Tab.SHOP -> ShopScreen(s, vm::buy, vm::togglePlace, vm::toggleWear)
                    Tab.DEX -> DexScreen(s)
                    Tab.SETTINGS -> SettingsScreen(canDrawOverlays, notificationsAllowed)
                }
            }
        }
    }

    // 하루 한 번 앱을 열면 선물 상자 (SPEC 5-2)
    val gift by vm.gift.collectAsStateWithLifecycle()
    val s = state
    if (gift != null || (s != null && evolution == null && fusion == null && DailyRules.giftAvailable(s.game, vm.today()))) {
        GiftDialog(gift, vm::openGift, vm::closeGift)
    }

    evolution?.let { EvolutionScreen(it, vm::evolutionShown) }
    fusion?.let { FusionScreen(it, vm::fusionShown) }
}

@Composable
private fun EvolveButtons(state: GameState, onEvolve: (CatId) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (id in CatId.entries) {
            val cat = state.cat(id)
            if (!cat.evolutionReady) continue
            Button(onClick = { onEvolve(id) }, modifier = Modifier.padding(4.dp)) {
                Text("✨ ${Form.of(id, cat.stage).monName}이 진화할 수 있어요! 진화하기")
            }
        }
    }
}
