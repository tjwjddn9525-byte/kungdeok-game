package com.kungdeok.game

import android.os.Bundle
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
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { KungdeokTheme { App(vm) } }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.ROOM) }
    var target by rememberSaveable { mutableStateOf(Target.BOTH) }
    val snackbar = remember { SnackbarHostState() }

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
                    Tab.ROOM -> RoomScreen(s, now, target, { target = it }, { vm.care(target.cats, it) })
                    Tab.STATUS -> StatusScreen(s, now)
                }
            }
        }
    }
}
