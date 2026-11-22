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
import com.kungdeok.game.ui.GameApp
import com.kungdeok.game.ui.MainViewModel
import com.kungdeok.game.ui.RoomScreen
import com.kungdeok.game.ui.StatusScreen
import com.kungdeok.game.ui.Target

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val steps by lazy { StepTracker(this, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KungdeokTheme { GameApp(vm.controller) } }
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

